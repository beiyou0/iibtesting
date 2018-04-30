package org.eventfully.testcase;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.XMLUnit;
import org.dom4j.DocumentException;
import org.dom4j.Element;
import org.eventfully.wmbtesting.EmptyRouteCamelConfiguration;
import org.eventfully.wmbtesting.IgnoreNamedElementsDifferenceListener;
import org.eventfully.wmbtesting.WmqUtil;
import org.junit.*;
import org.junit.runner.RunWith;
import org.junit.runners.MethodSorters;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;
import org.xml.sax.SAXException;

import java.io.*;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;

import static junit.framework.Assert.*;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;
import static org.custommonkey.xmlunit.XMLAssert.assertXpathEvaluatesTo;

/**
 * Created by qianqian on 05/07/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(classes = EmptyRouteCamelConfiguration.class)
public class RightTimeBGTest {
    @Autowired
    public CamelContext camelContext;

    public ProducerTemplate producer;
    public ConsumerTemplate consumer;

    private static String resourcePath;
    private static String dataPath;
    private static String propFile;
    private static String inputFileType1;
    private static String inputFileType2;
    private static String perSOFile;
    private static String archTemplateFile;

    @BeforeClass
    public static void setUpBeforeClass() throws IOException {
        resourcePath = RightTimeBGTest.class.getClass().getResource("/RightTime").getPath() + "/";
        propFile = resourcePath + "BG.properties";
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        dataPath = resourcePath + prop.getProperty("BG.rootDir");
        archTemplateFile = resourcePath + prop.getProperty("BG.archiveTemplateXML");
        inputFileType1 = dataPath + prop.getProperty("BG.inputXML.type1");
        inputFileType2 = dataPath + prop.getProperty("BG.inputXML.type2");
        perSOFile = dataPath + prop.getProperty("BG.type2.preSOXML");
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
    public void rightTimeBGForArchiveQ() throws Exception {
        File testPayload = new File(inputFileType1);
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.BG/ARCHIVEQUEUE";
        String[] ignoreNamedElementsNames = {"WBI_TIME"};

        byte[] preCorrelationId = WmqUtil.getRandomByte24NumArray();
        String correlationId = WmqUtil.getHexString(preCorrelationId);

        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSCorrelationID", preCorrelationId);

        producer.sendBodyAndHeaders("wmq:" + requestQ, testPayload, headers);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        assertNotNull("Reply should not be null", reply);

        String archiveMsg = getBGArchMsgFromTemplateFile(testPayload, new File(archTemplateFile));

        Diff myDiff = new Diff(new StringReader(archiveMsg), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Should have same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);
    }

    @Test
    public void rightTimeBGForDBTableRTBilling() throws DocumentException, IOException {
        final String prefix = "Header001.";
        final String parEleStr = "Header001.ParentEle";
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        File testPayload = new File(inputFileType1);

        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("BoEvtId", "//BoEvtId");
        xpathMap.put("SapBillgDocNum", "//SapZodsbillingZ2odsbillHeader001/SapBillgDocNum");
        Map<String, Object> headers = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);

        // ****** Step #1: check the added records num in table SODS0.RT_BILLING every input file to INPUTQUEUE ******
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsbillingZ2odsbillHeader001");
        Long beCount = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLING.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        producer.sendBody("wmq:" + requestQ, testPayload);

        Long afCount = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLING.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        Long count = afCount - beCount;
        assertEquals("Should have same number items inserted into table.", expectedRecords, count.longValue());

        // ****** Step #2: check every field which xml file has in table SODS0.RT_BILLING
        Map<String, Element> map = new HashMap<>();
        WmqUtil.addOneToXMLAndDBMapByEleName(map, testPayload, propFile, "BoEvtId", "SapZodsbilling.");
        WmqUtil.addAllToXMLAndDBMapByParentName(map, testPayload, propFile, parEleStr, prefix);

        List<Map<String, Object>> mainList = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLING.select}}?dataSourceRef=db2DS", "", headers, List.class);
        assertTrue("Should have data in table - RT_BILLING.", mainList.size() != 0);
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
    public void rightTimeBGForDBTableRTBillgLineItem() throws DocumentException, IOException {
        final String prefix = "Item003.";
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        File testPayload = new File(inputFileType1);

        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("BoEvtId", "//BoEvtId");
        Map<String, Object> headers = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);

        // ****** Step #1: check the added records num in table SODS0.RT_BILLG_LINE_ITEM every input file to INPUTQUEUE ******
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsbillingZ2odsbillLineItem003");
        Long beCount = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLG_LINE_ITEM.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        producer.sendBody("wmq:" + requestQ, testPayload);

        Long afCount = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLG_LINE_ITEM.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        Long count = afCount - beCount;
        assertEquals("Should have same number items inserted into table.", expectedRecords, count.longValue());

        // ****** Step #2: check every field which xml file has in table SODS0.RT_BILLING
        for (int i = 1; i < expectedRecords + 1; i++) {
            String parXpath = "//SapZodsbillingZ2odsbillLineItem003[" + i + "]";
            Map<String, Element> map = new HashMap<>();
            WmqUtil.addOneToXMLAndDBMapByEleName(map, testPayload, propFile, "BoEvtId", "SapZodsbilling.");
            WmqUtil.addAllToXMLAndDBMapByParentXpath(map, testPayload, propFile, parXpath, prefix);

            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "BillgLineItemSeqNum", parXpath + "/BillgLineItemSeqNum");
            List<Map<String, Object>> mainList = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLG_LINE_ITEM.select}}?dataSourceRef=db2DS", "", headers, List.class);
            assertTrue("Should have data in table - RT_BILLG_LINE_ITEM.",mainList.size() != 0);
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
    public void rightTimeBGForDBTableRTBillgPriceCond() throws DocumentException, IOException {
        final String prefix = "Pricing000.";
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        File testPayload = new File(inputFileType1);

        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("BoEvtId", "//BoEvtId");
        Map<String, Object> headers = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(headers, testPayload, xpathMap);

        // ****** Step #1: check the added records num in table SODS0.RT_BILLG_LINE_ITEM every input file to INPUTQUEUE ******
        int expectedRecords = WmqUtil.getCountOfElementByName(testPayload,"SapZodsbillingZ2odsbillPricing000");
        Long beCount = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLG_PRICE_COND.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        producer.sendBody("wmq:" + requestQ, testPayload);

        Long afCount = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLG_PRICE_COND.count}}?dataSourceRef=db2DS&outputType=SelectOne", "", headers, Long.class);
        Long count = afCount - beCount;
        assertEquals("Should have same number items inserted into table.", expectedRecords, count.longValue());

        // ****** Step #2: check every field which xml file has in table SODS0.RT_BILLING
        for (int i = 1; i < expectedRecords + 1; i++) {
            String parXpath = "//SapZodsbillingZ2odsbillPricing000[" + i + "]";
            Map<String, Element> map = new HashMap<>();
            WmqUtil.addOneToXMLAndDBMapByEleName(map, testPayload, propFile, "BoEvtId", "SapZodsbilling.");
            WmqUtil.addAllToXMLAndDBMapByParentXpath(map, testPayload, propFile, parXpath, prefix);

            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "BillgLineItemSeqNum", parXpath + "/BillgLineItemSeqNum");
            WmqUtil.addEleTextMapByEleXpathString(headers, testPayload, "SapPriceCondCode", parXpath + "/SapPriceCondCode");
            List<Map<String, Object>> mainList = producer.requestBodyAndHeaders("sql:{{sql.BG.RT_BILLG_PRICE_COND.select}}?dataSourceRef=db2DS", "", headers, List.class);
            assertTrue("Should have data in table - RT_BILLG_PRICE_COND.",mainList.size() != 0);
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
    // add for new feature which handling SO and BG time issue - US1342588
    // Type2: SO archiveQ do not have, msg goes into BG inputQ to wait
    public void rightTimeBGType2ForInputQ() throws Exception {
        File testPayload = new File(inputFileType2);
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        String[] ignoreNamedElementsNames = {""};

        String SapSalesOrdNum = WmqUtil.getEleContextByXpath(testPayload, "//SapZodsbillingZ2odsbillLineItem003[1]/SapSalesOrdNum");
        String correlationId = WmqUtil.jmsCorrelationIdOfRTFlow(SapSalesOrdNum);
        producer.sendBody("wmq:" + requestQ, testPayload);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        assertNotNull(reply);

        Diff myDiff = new Diff(new FileReader(testPayload), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Should have same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);
    }

    @Test
    // add for new feature which handling SO and BG time issue - US1342588
    // Type2: SO archiveQ dose have, msg goes into BG archiveQ
    public void rightTimeBGType2ForArchiveQ() throws DocumentException, IOException, SAXException {
        File preSOFile = new File(perSOFile);
        String preQueue = "SWG.SAP.ODS.SO/INPUTQUEUE";
        String postQueue = "SWG.SAP.ODS.SO/ARCHIVEQUEUE";

        File testPayload = new File(inputFileType2);
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.BG/ARCHIVEQUEUE";
        String[] ignoreNamedElementsNames = {"WBI_TIME"};

        // preparation: send sales order msg to SO flow.
        producer.sendBody("wmq:" + preQueue, preSOFile);

        // send billing msg
        String SapSalesOrdNum = WmqUtil.getEleContextByXpath(testPayload, "//SapZodsbillingZ2odsbillLineItem003[1]/SapSalesOrdNum");
        String correlationId = WmqUtil.jmsCorrelationIdOfRTFlow(SapSalesOrdNum);
        producer.sendBody("wmq:" + requestQ, testPayload);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        assertNotNull(reply);

        String archiveMsg = getBGArchMsgFromTemplateFile(testPayload, new File(archTemplateFile));

        Diff myDiff = new Diff(new StringReader(archiveMsg), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Should have same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);

        // clear up: get msg from SO archive queue
        String replySO = consumer.receiveBody("wmq:" + postQueue +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        assertNotNull(replySO);
    }

    // EMH - Error Message Handling
    @Test
    public void rightTimeBGForEMHBlankBO() throws Exception {
        File testPayload = new File(dataPath + "/BG_EMHBlankBO.xml");
        String requestQ = "SWG.SAP.ODS.BG/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.BG/ARCHIVEQUEUE";

        byte[] preCorrelationId = WmqUtil.getRandomByte24NumArray();
        String correlationId = WmqUtil.getHexString(preCorrelationId);

        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSCorrelationID", preCorrelationId);

        producer.sendBodyAndHeaders("wmq:" + requestQ, testPayload, headers);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        System.out.println("******* reply is: " + reply + " ********");
        assertNotNull("Reply is null. Please check.", reply);
        assertXpathEvaluatesTo("Blank BO.  Procedure Did Not Get Called","/Billing/Status/Message", reply);
    }

    public String getBGArchMsgFromTemplateFile(File testPayload, File templateFile) throws DocumentException, IOException {
        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("@EVENT_ID@", "/SapZodsbilling/EvtId");
        xpathMap.put("@BO_EVENT_ID@", "/SapZodsbilling/BoEvtId");
        xpathMap.put("@EVENT_DATE@", "/SapZodsbilling/EvtDate");
        xpathMap.put("@EVENT_TIME@", "/SapZodsbilling/EvtTime");
        xpathMap.put("@BILLING_DOC_NUM@", "//SapZodsbillingZ2odsbillHeader001/SapBillgDocNum");

        Map<String, Object> map = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(map, testPayload, xpathMap);
        return WmqUtil.replaceInFile(templateFile, map);
    }
}
