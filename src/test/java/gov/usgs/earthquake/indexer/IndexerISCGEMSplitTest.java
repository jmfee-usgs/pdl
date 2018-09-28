package gov.usgs.earthquake.indexer;

import java.io.File;
import java.util.LinkedList;
import java.util.logging.ConsoleHandler;
import java.util.logging.Level;
import java.util.logging.LogManager;
import java.util.logging.Logger;

import gov.usgs.earthquake.distribution.FileProductStorage;
import gov.usgs.earthquake.indexer.IndexerChange.IndexerChangeType;
import gov.usgs.earthquake.product.Product;
import gov.usgs.earthquake.product.io.ObjectProductHandler;
import gov.usgs.earthquake.product.io.XmlProductSource;
import gov.usgs.util.FileUtils;
import gov.usgs.util.StreamUtils;
import gov.usgs.util.logging.SimpleLogFormatter;

import org.junit.Assert;
import org.junit.After;
import org.junit.Before;
import org.junit.Test;

/**
 * Reproduce a (false) event split that led to issues in comcat.
 * 
 * 2018-09-12
 * 
 * @author jmfee
 * 
 */
public class IndexerISCGEMSplitTest {

	private static File STORAGE_FILE = new File("_test_storage");
	private static File INDEX_FILE = new File("_test_index");

	private static String[] PRODUCTS = new String[] {
		"etc/test_products/iscgem_split/0_us_origin_iscgem805430_1423777364185.xml",
		"etc/test_products/iscgem_split/1_us_origin_iscgemsup805431_1436806944000.xml",
		"etc/test_products/iscgem_split/2_us_origin_iscgemsup805429_1436806944000.xml",
		"etc/test_products/iscgem_split/3_us_origin_iscgem805430_1422122876000.xml",
		"etc/test_products/iscgem_split/4_atlas_shakemap_atlas19690811212737_1490049406234.xml",
		"etc/test_products/iscgem_split/5_atlas_shakemap_atlas19690811212737_1491953323895.xml",
		"etc/test_products/iscgem_split/6_atlas_trump-shakemap_atlas19690811212737_1492119847204.xml",
		"etc/test_products/iscgem_split/7_atlas_shakemap_atlas19690811212737_1492019329065.xml",
		"etc/test_products/iscgem_split/8_us_origin_iscgemsup805431_1536770083065.xml",
		"etc/test_products/iscgem_split/9_admin_trump-origin_iscgem805430_1536770122417.xml",
		"etc/test_products/iscgem_split/10_us_origin_iscgem805430_1431543183000.xml",
		"etc/test_products/iscgem_split/11_us_touch_iscgem805430_1536773688128.xml"
	};

	private static final Object SYNC = new Object();

	@Before
	public void before() {
		FileUtils.deleteTree(STORAGE_FILE);
		FileUtils.deleteTree(INDEX_FILE);
	}

	@After
	public void after() {
		FileUtils.deleteTree(STORAGE_FILE);
		FileUtils.deleteTree(INDEX_FILE);
	}

	@Test
	public void testTrumpAfterDelete() throws Exception {
		IndexerEvent lastEvent;
		IndexerChange lastChange;

		// turn up logging during test
		LogManager.getLogManager().reset();
		ConsoleHandler handler = new ConsoleHandler();
		// handler.setLevel(Level.FINEST);
		handler.setFormatter(new SimpleLogFormatter());
		handler.setLevel(Level.FINEST);
		Logger rootLogger = Logger.getLogger("");
		rootLogger.addHandler(handler);
		rootLogger.setLevel(Level.FINEST);

		Indexer indexer = new Indexer();
		indexer.setProductIndex(new JDBCProductIndex(INDEX_FILE.getName()));
		indexer.setProductStorage(new FileProductStorage(STORAGE_FILE));

		TestIndexerListener listener = new TestIndexerListener();
		indexer.addListener(listener);

		indexer.startup();

		// load test products
		LinkedList<Product> products = new LinkedList<Product>();
		for (String productPath : PRODUCTS) {
			products.add(ObjectProductHandler.getProduct(new XmlProductSource(
				StreamUtils.getInputStream(new File(productPath)))));
		}

		// set up state before delete and trump were sent.
		for (int i = 0; i < 8; i++) {
			indexer.onProduct(products.remove(0));
			// wait for listener to be notified
			synchronized (SYNC) {
				SYNC.wait();
			}
		}

		// now index remaining products
		Assert.assertEquals("4 products remaining", 4, products.size());

		System.err.println("\n\nDeleting iscgemsup805431");
		// delete iscgemsup805431
		indexer.onProduct(products.remove(0));
		// wait for listener to be notified
		synchronized (SYNC) {
			SYNC.wait();
		}
		// event iscgemsup805431 deleted, iscgemsup805429 merged
		lastEvent = listener.getLastEvent();
		Assert.assertEquals("expect 2 changes",
				2, lastEvent.getIndexerChanges().size());
		lastChange = lastEvent.getIndexerChanges().get(0);
		Assert.assertEquals("expect first change type to be merge",
				IndexerChangeType.EVENT_MERGED, lastChange.getType());
		Assert.assertEquals("expect id that was merged to be iscgemsup805429",
				"iscgemsup805429", lastChange.getOriginalEvent().getEventId());
		lastChange = lastEvent.getIndexerChanges().get(1);
		Assert.assertEquals("expect second change type to be updated",
				IndexerChangeType.EVENT_UPDATED, lastChange.getType());
		Assert.assertEquals("expect old id to be iscgemsup805431",
				"iscgemsup805431", lastChange.getOriginalEvent().getEventId());
		Assert.assertEquals("expect new id to be iscgemsup805429",
				"iscgemsup805429", lastChange.getNewEvent().getEventId());


		// trump iscgem805430
		indexer.onProduct(products.remove(0));
		// wait for listener to be notified
		synchronized (SYNC) {
			SYNC.wait();
		}
		// event iscgem805430 trumped, iscgemsup805431 should not split
		lastEvent = listener.getLastEvent();
		Assert.assertEquals("expect 1 change",
				1, lastEvent.getIndexerChanges().size());
		// verify event ids updated as expected
		lastChange = lastEvent.getIndexerChanges().get(0);
		Assert.assertEquals("expect change type to be updated",
				IndexerChangeType.EVENT_UPDATED, lastChange.getType());
		Assert.assertEquals("expect old id to be iscgemsup805429",
				"iscgemsup805429", lastChange.getOriginalEvent().getEventId());
		Assert.assertEquals("expect new id to be iscgem805430",
				"iscgem805430", lastChange.getNewEvent().getEventId());

		// TODO: replace this.
		while (products.size() > 0) {
			indexer.onProduct(products.remove(0));
			synchronized (SYNC) {
				SYNC.wait();
			}
		}

		indexer.shutdown();
	}

	public class TestIndexerListener extends DefaultIndexerListener {
		private IndexerEvent lastIndexerEvent = null;

		public TestIndexerListener() {
		}

		@Override
		public void onIndexerEvent(final IndexerEvent event) {
			try {
				Thread.sleep(5);
			} catch (InterruptedException e) {
				e.printStackTrace();
			}
			lastIndexerEvent = event;
			synchronized (SYNC) {
				SYNC.notify();
			}
		}

		public IndexerEvent getLastEvent() throws InterruptedException {
			return lastIndexerEvent;
		}

		public void clear() {
			lastIndexerEvent = null;
		}

	}

}
