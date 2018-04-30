package org.eventfully.testcase;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.XMLUnit;
import org.dom4j.*;
import org.dom4j.io.SAXReader;
import org.eventfully.wmbtesting.EmptyRouteCamelConfiguration;
import org.eventfully.wmbtesting.IgnoreNamedElementsDifferenceListener;
import org.eventfully.wmbtesting.WmqUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.*;
import java.util.*;

import static junit.framework.Assert.assertEquals;
import static junit.framework.Assert.assertNotNull;
import static junit.framework.Assert.assertTrue;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;


/**
 * Created by qianqian on 23/05/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(classes = EmptyRouteCamelConfiguration.class)
public class RightTimeSOTest {

    @Autowired
    public CamelContext camelContext;

    public ProducerTemplate producer;
    public ConsumerTemplate consumer;

    private static String propFile;
    private static String inputFile;
    private static String archTemplateFile;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        String path = RightTimeSOTest.class.getClass().getResource("/RightTime").getPath() + "/";
        propFile = path + "SO.properties";
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        String dataDir = path + prop.getProperty("SO.rootDir");
        archTemplateFile = path + prop.getProperty("SO.archiveTemplateXML");
        inputFile = dataDir + prop.getProperty("SO.inputXML");
    }

    @Before
    public void setUp() {
        XMLUnit.setIgnoreWhitespace(true);
        producer = camelContext.createProducerTemplate();
        consumer = camelContext.createConsumerTemplate();
    }

    @After
    public void tearDown() {
        producer = null;
        consumer = null;
    }

    @Test
    public void rightTimeSOForArchiveQ() throws Exception {
        File testPayload = new File(inputFile);
        String requestQ = "SWG.SAP.ODS.SO/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.SO/ARCHIVEQUEUE";
        String[] ignoreNamedElementsNames = {"WBI_TIME"};

        String Vbeln = WmqUtil.getEleContextByXpath(testPayload, "//SapZodsorderZ2odsordSalesOrd000/Vbeln");
        String correlationId = WmqUtil.jmsCorrelationIdOfRTFlow(Vbeln);
        producer.sendBody("wmq:" + requestQ, testPayload);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        assertNotNull("Reply should not be null.", reply);

        String archiveMsg = getSOArchMsgFromTemplateFile(testPayload, new File(archTemplateFile));
        System.out.println(archiveMsg);

        Diff myDiff = new Diff(new StringReader(archiveMsg), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Should have same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);
    }

    @Test
    public void rightTimeSOForDBTableRTSalesORD() throws Exception {
        final String prefixSO000 = "SalesOrd000.";
        final String parEleSO000 = "SalesOrd000.ParentEle";
        final String prefixSO1000 = "SalesOrd1000.";
        final String parEleSO1000 = "SalesOrd1000.ParentEle";

        File testPayload = new File(inputFile);
        String requestQ = "SWG.SAP.ODS.SO/INPUTQUEUE";

        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("BoEvtId", "/SapZodsorder/BoEvtId");
        xpathMap.put("Vbeln", "//SapZodsorderZ2odsordSalesOrd000/Vbeln");
        Map<String, Object> headers = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);

        // ****** Step #1: check the added records num in table SODS0.RT_SALES_ORD every input file to INPUTQUEUE ******
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsorderZ2odsordSalesOrd000");
        Long beCount = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        producer.sendBody("wmq:" + requestQ, testPayload);

        Long afCount = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        Long count = afCount - beCount;
        assertEquals("Should have same number items inserted into table.", expectedRecords, count.longValue());

        // ****** Step #2: check every field which xml file has in table SODS0.RT_SALES_ORD
        Map<String, Element> map = new HashMap<>();
        WmqUtil.addOneToXMLAndDBMapByEleName(map, testPayload, propFile, "BoEvtId", "SapZodsorder.");
        WmqUtil.addAllToXMLAndDBMapByParentName(map, testPayload, propFile, parEleSO000, prefixSO000);
        WmqUtil.addAllToXMLAndDBMapByParentName(map, testPayload, propFile, parEleSO1000, prefixSO1000);

        List<Map<String, Object>> mainList = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD.select}}?dataSourceRef=db2DS", "", headers, List.class);
        assertTrue("Should have data in table - RT_SALES_ORD.",mainList.size() != 0);
        for(Map<String, Object> m : mainList) {
            for (String colInDB: m.keySet()) {
                Element entry = map.get(colInDB);
                if (entry != null) {
                    assertEquals("Column: " + colInDB + " should have the same value.", entry.getText().trim(), m.get(colInDB).toString().trim());
                }
            }
        }
    }

    @Test
    public void rightTimeSOForDBTableRTSalesORDLineItem() throws DocumentException, IOException {
        final String prefixLI000 = "LineItem000.";
        final String prefixLI1000 = "LineItem1000.";
        final String prefixLI2000 = "LineItem2000.";

        File testPayload = new File(inputFile);
        String requestQ = "SWG.SAP.ODS.SO/INPUTQUEUE";

        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("BoEvtId", "/SapZodsorder/BoEvtId");
        Map<String, Object> headers = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);

        // ****** Step #1: check the added records num in table SODS0.RT_SALES_ORD_LINE_ITEM every input file to INPUTQUEUE ******
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsorderZ2odsordLineItem002");
        Long beCount = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD_LINE_ITEM.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        producer.sendBody("wmq:" + requestQ, testPayload);

        Long afCount = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD_LINE_ITEM.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        Long count = afCount - beCount;
        assertEquals("Should have same number items inserted into table.", expectedRecords, count.longValue());

        // ****** Step #2: check every field which xml file has in table SODS0.RT_SALES_ORD_LINE_ITEM
        for (int i = 1; i < expectedRecords + 1; i++) {
            String xpathLI000 = "//SapZodsorderZ2odsordLineItem002[" + i + "]";
            String xpathLI1000 = "//SapZodsorderZ2odsordLineItem1001[" + i + "]";
            String xpathLI2000 = "//SapZodsorderZ2odsordLineItem2002[" + i + "]";
            Map<String, Element> map = new HashMap<>();
            WmqUtil.addOneToXMLAndDBMapByEleName(map, testPayload, propFile, "BoEvtId", "SapZodsbilling.");
            WmqUtil.addAllToXMLAndDBMapByParentXpath(map, testPayload, propFile, xpathLI000, prefixLI000);
            WmqUtil.addAllToXMLAndDBMapByParentXpath(map, testPayload, propFile, xpathLI1000, prefixLI1000);
            WmqUtil.addAllToXMLAndDBMapByParentXpath(map, testPayload, propFile, xpathLI2000, prefixLI2000);

            // special field: LineItem1000.Zztouname=TOU_NAME,varchar (LineItem1000.Zztouname1)
            specialFieldRTSalesORDLineItem(map, "TOU_NAME", xpathLI1000 + "/Zztouname", xpathLI1000 + "/Zztouname1");
            // special field: LineItem1000.Zztouurl=TOU_URL,varchar (LineItem1000.Zztouname1)
            specialFieldRTSalesORDLineItem(map, "TOU_URL", xpathLI2000 + "/Zztouurl", xpathLI2000 + "/Zztouurl1");

            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "Vbeln", xpathLI000 + "/Vbeln");
            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "Posnr", xpathLI000 + "/Posnr");
            List<Map<String, Object>> mainList = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD_LINE_ITEM.select}}?dataSourceRef=db2DS", "", headers, List.class);
            assertTrue("Should have data in table - RT_SALES_ORD_LINE_ITEM.",mainList.size() != 0);
            for(Map<String, Object> m : mainList) {
                for (String colInDB: m.keySet()) {
                    Element entry = map.get(colInDB);
                    if (entry != null) {
                        assertEquals("Record " + i + " - Column: " + colInDB + " should have the same value.", entry.getText().trim(), m.get(colInDB).toString().trim());
                    }
                }
            }
        }
    }

    @Test
    public void rightTimeSOForRTSalesORDPromoCodeLookUp() throws DocumentException, IOException, InterruptedException {
        boolean isExistCode1;
        boolean isExistCode2;
        boolean isExistCode3;
        File testPayload = new File(inputFile);
        String requestQ = "SWG.SAP.ODS.SO/INPUTQUEUE";

        producer.sendBody("wmq:" + requestQ, testPayload);
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsorderZ2odsordLineItem002");
        for (int i = 1; i < expectedRecords + 1; i++) {
            String xpathLI000 = "//SapZodsorderZ2odsordLineItem002[" + i + "]/";
            String xpathLI2000 = "//SapZodsorderZ2odsordLineItem2002[" + i + "]/";
            List<String> list = new ArrayList<>();

            isExistCode1 = WmqUtil.isEleExistByXpath(testPayload, xpathLI2000 + "Zzpromocode1");
            isExistCode2 = WmqUtil.isEleExistByXpath(testPayload, xpathLI2000 + "Zzpromocode2");
            isExistCode3 = WmqUtil.isEleExistByXpath(testPayload, xpathLI2000 + "Zzpromocode3");
            if (!isExistCode1 && !isExistCode2 && !isExistCode3) {
                continue;
            }
            if (isExistCode1) {
                list.add(WmqUtil.getEleContextByXpath(testPayload, xpathLI2000 + "Zzpromocode1"));
            }
            if (isExistCode2) {
                list.add(WmqUtil.getEleContextByXpath(testPayload, xpathLI2000 + "Zzpromocode2"));
            }
            if (isExistCode3) {
                list.add(WmqUtil.getEleContextByXpath(testPayload, xpathLI2000 + "Zzpromocode3"));
            }

            Map<String, String> xpathMap = new HashMap<>();
            xpathMap.put("BoEvtId", "/SapZodsorder/BoEvtId");
            Map<String, Object> headers = new HashMap<>();
            WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);
            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "Vbeln", xpathLI000 + "Vbeln");
            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "Posnr", xpathLI000 + "Posnr");

//            Thread.sleep(3000);
            List<Map<String, String>> mainList = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SALES_ORD_PROMO_CODE_LOOKUP.select}}?dataSourceRef=db2DS", "", headers, List.class);
            assertTrue("Should have data in table - RT_SALES_ORD_PROMO_CODE_LOOKUP.", mainList.size() != 0);
            for(Map<String, String> m : mainList) {
                for(String key: m.keySet()) {
                    assertTrue("Promo_Code field does not have the right value.", list.contains(m.get(key).trim()));
                }
            }
        }
    }

    @Test
    public void rightTimeSOForRTSlashdSalesORDLineItem() throws DocumentException, IOException {
        final String prefixSLI000 = "SlashdLineItem000.";
        File testPayload = new File(inputFile);
        String requestQ = "SWG.SAP.ODS.SO/INPUTQUEUE";

        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("BoEvtId", "/SapZodsorder/BoEvtId");
        Map<String, Object> headers = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);

        // ****** Step #1: check the added records num in table SODS0.RT_SALES_ORD_LINE_ITEM every input file to INPUTQUEUE ******
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsorderZ2odsordSlashdLineItem000");
        Long beCount = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SLASHD_SALES_ORD_LINE_ITEM.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        System.out.println("******" + beCount);
        producer.sendBody("wmq:" + requestQ, testPayload);

        Long afCount = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SLASHD_SALES_ORD_LINE_ITEM.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        System.out.println("******" + afCount);
        Long count = afCount - beCount;
        assertEquals("Should have same number items inserted into table.", expectedRecords, count.longValue());

        for (int i = 1; i < expectedRecords + 1; i++) {
            String xpathSLI000 = "//SapZodsorderZ2odsordSlashdLineItem000[" + i + "]/";
            Map<String, Element> map = new HashMap<>();
            WmqUtil.addOneToXMLAndDBMapByEleName(map, testPayload, propFile, "BoEvtId", "SapZodsbilling.");
            WmqUtil.addAllToXMLAndDBMapByParentXpath(map, testPayload, propFile, xpathSLI000, prefixSLI000);

            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "Vbeln", xpathSLI000 + "Vbeln");
            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "Posnr", xpathSLI000 + "Posnr");
            List<Map<String, Object>> mainList = producer.requestBodyAndHeaders("sql:{{sql.SO.RT_SLASHD_SALES_ORD_LINE_ITEM.select}}?dataSourceRef=db2DS", "", headers, List.class);
            assertTrue("Should have data in table - RT_SLASHD_SALES_ORD_LINE_ITEM.",mainList.size() != 0);
            for(Map<String, Object> m : mainList) {
                for (String colInDB: m.keySet()) {
                    Element entry = map.get(colInDB);
                    if (entry != null) {
                        assertEquals("Record " + i + " - Column: " + colInDB + " should have the same value.", entry.getText().trim(), m.get(colInDB).toString().trim());
                    }
                }
            }
        }
    }

    @Test
    public void rightTimeSOForForEMHBlankBO() throws Exception {
        File testPayload = new File("src/test/resources/RightTime/data/SO_error.xml");
        String requestQ = "SWG.SAP.ODS.SO/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.SO/ARCHIVEQUEUE";

        byte[] preCorrelationId = WmqUtil.getRandomByte24NumArray();
        String correlationId = WmqUtil.getHexString(preCorrelationId);

        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSCorrelationID", preCorrelationId);

        producer.sendBodyAndHeaders("wmq:" + requestQ, testPayload, headers);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        System.out.println("******* reply is: " + reply + " ********");
        assertNotNull("Reply is null. Please check.", reply);
        assertXpathEvaluatesTo("Blank BO.  Procedure Did Not Get Called","/SalesOrder/Status/Message", reply);
    }

    public void specialFieldRTSalesORDLineItem(Map<String, Element> map, String key, String xpath, String xpath1) throws DocumentException {
        // special data processing for RT_SALES_ORD_LINE_ITEM: 1.LineItem1000.Zztouname=TOU_NAME,varchar (LineItem1000.Zztouname1)
        //                                                     2.LineItem2000.Zztouurl=TOU_URL,varchar (LineItem2000.Zztouurl1)
        SAXReader sax = new SAXReader();
        Document doc = sax.read(inputFile);
        Node tou = doc.selectSingleNode(xpath);
        Node tou1 = doc.selectSingleNode(xpath1);

        if(tou1 != null) {
            String context;
            if(tou != null) {
                String text = tou.getText().trim();
                context = String.format("%-200s", text) + tou1.getText();
                assertTrue("Object should not be null", map.get(key) != null);
                map.get(key).setText(context);
            }
            else{
                map.put(key, (Element) tou1);
            }
        }
    }

    public String getSOArchMsgFromTemplateFile(File testPayload, File templateFile) throws DocumentException, IOException {
        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("@Main_EVENT_ID@", "/SapZodsorder/EvtId");
        xpathMap.put("@Main_BO_EVENT_ID@", "/SapZodsorder/BoEvtId");
        xpathMap.put("@Main_EVENT_DATE@", "/SapZodsorder/EvtDate");
        xpathMap.put("@Main_EVENT_TIME@", "/SapZodsorder/EvtTime");
        xpathMap.put("@Main_SALES_ORDER_ID@", "//SapZodsorderZ2odsordSalesOrd000/Vbeln");

        xpathMap.put("@Doc_EVENT_ID@", "//SapZodsorderZ2odsstatMain00064864807/EvtId");
        xpathMap.put("@Doc_BO_EVENT_ID@", "//SapZodsorderZ2odsstatMain00064864807/BoEvtId");
        xpathMap.put("@Doc_EVENT_DATE@", "//SapZodsorderZ2odsstatMain00064864807/EvtDate");
        xpathMap.put("@Doc_EVENT_TIME@", "//SapZodsorderZ2odsstatMain00064864807/EvtTime");


        Map<String, Object> map = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(map, testPayload, xpathMap);
        return WmqUtil.replaceInFile(templateFile, map);
    }
}
