package gov.usgs.earthquake.distribution;

import java.io.BufferedReader;
import java.io.File;
import java.io.InputStreamReader;
import java.security.PrivateKey;
import java.util.Iterator;
import java.util.Map;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.logging.Level;
import java.util.logging.Logger;

import gov.usgs.earthquake.product.Product;
import gov.usgs.earthquake.product.io.IOUtil;
import gov.usgs.earthquake.product.io.ObjectProductHandler;
import gov.usgs.earthquake.product.io.ProductSource;
import gov.usgs.util.CryptoUtils;
import gov.usgs.util.FileUtils;
import gov.usgs.util.CryptoUtils.Version;

/**
 * A utility class to (re)send an existing product to pdl hubs.
 *
 * Mainly used when one server has not received a product, in order to
 * redistribute the product.
 */
public class ProductResender {

	private static final Logger LOGGER = Logger.getLogger(ProductResender.class
			.getName());

	public static final String SERVERS_ARGUMENT = "--servers=";
	public static final String BATCH_ARGUMENT = "--batch";
	public static final String CONCURRENCY_ARGUMENT = "--concurrency=";

	public static class PartiallySentError extends Exception {
		private static final long serialVersionUID = 1L;
	}
	public static class UnableToSendError extends Exception {
		private static final long serialVersionUID = 1L;
	}

	public static void main(final String[] args) throws Exception {
		// disable tracker
		ProductTracker.setTrackerEnabled(false);

		boolean batchMode = false;
		boolean binaryFormat = false;
		int concurrency = 50;
		boolean enableDeflate = true;
		String inFile = null;
		String inFormat = null;
		String privateKey = null;
		String servers = null;
		Version signatureVersion = Version.SIGNATURE_V2;

		for (String arg : args) {
			if (arg.equals(BATCH_ARGUMENT)) {
				batchMode = true;
			} else if (arg.equals(CLIProductBuilder.BINARY_FORMAT_ARGUMENT)) {
				binaryFormat = true;
			} else if (arg.equals(CONCURRENCY_ARGUMENT)) {
				concurrency = Integer.parseInt(arg.replace(CONCURRENCY_ARGUMENT, ""));
			} else if (arg.equals(CLIProductBuilder.DISABLE_DEFLATE)) {
				enableDeflate = false;
			} else if (arg.startsWith(IOUtil.INFILE_ARGUMENT)) {
				inFile = arg.replace(IOUtil.INFILE_ARGUMENT, "");
			} else if (arg.startsWith(IOUtil.INFORMAT_ARGUMENT)) {
				inFormat = arg.replace(IOUtil.INFORMAT_ARGUMENT, "");
			} else if (arg.startsWith(CLIProductBuilder.PRIVATE_KEY_ARGUMENT)) {
				privateKey = arg.replace(CLIProductBuilder.PRIVATE_KEY_ARGUMENT, "");
			} else if (arg.startsWith(SERVERS_ARGUMENT)) {
				servers = arg.replace(SERVERS_ARGUMENT, "");
			} else if (arg.startsWith(CLIProductBuilder.SIGNATURE_VERSION_ARGUMENT)) {
				signatureVersion = Version.valueOf(
						arg.replace(CLIProductBuilder.SIGNATURE_VERSION_ARGUMENT, ""));
			}
		}

		// read product
		Product product = null;
		if (!batchMode) {
			product = loadProduct(inFormat, inFile);
		}

		ProductBuilder builder = new ProductBuilder();
		builder.getProductSenders().addAll(
				CLIProductBuilder.parseServers(servers, 15000, binaryFormat,
						enableDeflate));
		if (privateKey != null) {
			PrivateKey key = CryptoUtils.readOpenSSHPrivateKey(
					FileUtils.readFile(new File(privateKey)), null);
			if (key == null) {
				LOGGER.warning("Unable to load private key " + privateKey);
				System.exit(1);
			}
			builder.setPrivateKey(key);
		}
		builder.setSignatureVersion(signatureVersion);

		if ((!batchMode && product == null) || builder.getProductSenders().size() == 0) {
			System.err.println("Usage: ProductResender --servers=SERVERLIST"
					+ " --informat=(zip|directory|xml) --infile=FILE"
					+ " [--binaryFormat] [--disableDeflate] [--batch]");
			System.err.println("When using batch mode (--batch), the --infile argument is ignored.");
			System.err.println("Files to send are read one per line from stdin.");
			System.exit(CLIProductBuilder.EXIT_INVALID_ARGUMENTS);
		}

		builder.startup();

		if (!batchMode) {
			try {
				sendProduct(builder, product);
			} catch (PartiallySentError pse) {
				System.exit(CLIProductBuilder.EXIT_PARTIALLY_SENT);
			} catch (UnableToSendError use) {
				System.exit(CLIProductBuilder.EXIT_UNABLE_TO_SEND);
			}
		} else {
			// blocking executor to throttle send loop
			ExecutorService senderService = new ThreadPoolExecutor(
					concurrency, concurrency, 60, TimeUnit.SECONDS,
					new ArrayBlockingQueue<>(concurrency),
					Executors.defaultThreadFactory(),
					new ThreadPoolExecutor.CallerRunsPolicy()
			);
			// send batch
			BufferedReader br = new BufferedReader(new InputStreamReader(System.in));
			String line = null;
			while ((line = br.readLine()) != null) {
				// load product in this thread
				final Product productToSend = loadProduct(inFormat, line);
				if (productToSend == null) {
					System.err.println("ERROR: unable to load product from '" + line + "'");
				} else {
					// send in service
					senderService.execute(() -> {
						try {
							sendProduct(builder, productToSend);
						} catch (Exception e) {
							LOGGER.log(Level.WARNING, "Error sending product", e);
						}
					});
				}
			}
			// wait for sends to complete
			senderService.shutdown();
			senderService.awaitTermination(10, TimeUnit.MINUTES);
		}

		// normal exit
		builder.shutdown();
		System.exit(0);
	}

	protected static Product loadProduct(final String inFormat, final String path)
			throws Exception {
		// read product
		final ProductSource source = IOUtil.getProductSource(inFormat, new File(path));
		if (source == null) {
			return null;
		}
		return ObjectProductHandler.getProduct(source);
	}

	protected static void sendProduct(final ProductBuilder builder,
			final Product product) throws Exception {
		// extracted from CLIProductBuilder

		// send the product
		Map<ProductSender, Exception> sendExceptions = builder
				.sendProduct(product);

		// handle any send exceptions
		if (sendExceptions.size() != 0) {
			Iterator<ProductSender> senders = sendExceptions.keySet()
					.iterator();
			// log the exceptions
			while (senders.hasNext()) {
				ProductSender sender = senders.next();
				if (sender instanceof SocketProductSender) {
					// put more specific information about socket senders
					SocketProductSender socketSender = (SocketProductSender) sender;
					LOGGER.log(
							Level.WARNING,
							"Exception sending product to "
									+ socketSender.getHost() + ":"
									+ socketSender.getPort(),
							sendExceptions.get(sender));
				} else {
					LOGGER.log(Level.WARNING, "Exception sending product "
							+ sendExceptions.get(sender));
				}
			}

			if (sendExceptions.size() < builder.getProductSenders().size()) {
				LOGGER.warning("Partial failure sending product,"
						+ " at least one sender accepted product."
						+ " Check the tracker for more information.");
				// still output built product id
				System.out.println(product.getId().toString());
				throw new PartiallySentError();
			} else {
				LOGGER.severe("Total failure sending product");
				// still output built product id
				System.err.println("ERROR: " + product.getId().toString());
				throw new UnableToSendError();
			}
		}

		// otherwise output built product id
		System.out.println(product.getId().toString());
	}

}
