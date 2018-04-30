package org.eventfully.testcase;

import org.apache.camel.CamelContext;
import org.apache.camel.ConsumerTemplate;
import org.apache.camel.ProducerTemplate;
import org.custommonkey.xmlunit.Diff;
import org.custommonkey.xmlunit.DifferenceListener;
import org.custommonkey.xmlunit.XMLUnit;
import org.dom4j.DocumentException;
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
import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import static junit.framework.Assert.assertNotNull;
import static org.custommonkey.xmlunit.XMLAssert.assertXMLEqual;

/**
 * Created by qianqian on 05/07/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@FixMethodOrder(MethodSorters.NAME_ASCENDING)
@ContextConfiguration(classes = EmptyRouteCamelConfiguration.class)
public class RightTimeQUTest {
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
        propFile = path + "QU.properties";
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        String dataDir = path + prop.getProperty("QU.rootDir");
        archTemplateFile = path + prop.getProperty("QU.archiveTemplateXML");
        inputFile = dataDir + prop.getProperty("QU.inputXML");
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
    public void rightTimeQUForArchiveQ() throws Exception {
        File testPayload = new File(inputFile);
        String requestQ = "SWG.SAP.ODS.QU/INPUTQUEUE";
        String replyQ = "SWG.SAP.ODS.QU/ARCHIVEQUEUE";
        String[] ignoreNamedElementsNames = {"WBI_TIME"};

        byte[] preCorrelationId = WmqUtil.getRandomByte24NumArray();
        String correlationId = WmqUtil.getHexString(preCorrelationId);

        Map<String, Object> headers = new HashMap<>();
        headers.put("JMSCorrelationID", preCorrelationId);

        producer.sendBodyAndHeaders("wmq:" + requestQ, testPayload, headers);
        String reply = consumer.receiveBody("wmq:" + replyQ +"?selector=JMSCorrelationID = 'ID:" + correlationId + "'", 20000, String.class);
        assertNotNull("reply should not be null.", reply);
        System.out.println("******* reply is: " + reply + " ********");

        String archiveMsg = getQUArchMsgFromTemplateFile(testPayload, new File(archTemplateFile));
        System.out.println(archiveMsg);

        Diff myDiff = new Diff(new StringReader(archiveMsg), new StringReader(reply));
        DifferenceListener diffListener = new IgnoreNamedElementsDifferenceListener(ignoreNamedElementsNames);
        myDiff.overrideDifferenceListener(diffListener);
        assertXMLEqual("Should have same elements/attributes sequences, but some values may be ignored, normally time and dates.", myDiff, true);
    }

    public String getQUArchMsgFromTemplateFile(File testPayload, File templateFile) throws DocumentException, IOException {
        Map<String, String> xpathMap = new HashMap<>();
        xpathMap.put("@Main_EVENT_ID@", "/SapZodsquote/EvtId");
        xpathMap.put("@Main_BO_EVENT_ID@", "/SapZodsquote/BoEvtId");
        xpathMap.put("@Main_EVENT_DATE@", "/SapZodsquote/EvtDate");
        xpathMap.put("@Main_EVENT_TIME@", "/SapZodsquote/EvtTime");

        xpathMap.put("@Doc_EVENT_ID@", "//SapZodsquoteZ2odsstatMain0001409007221/EvtId");
        xpathMap.put("@Doc_BO_EVENT_ID@", "//SapZodsquoteZ2odsstatMain0001409007221/BoEvtId");
        xpathMap.put("@Doc_EVENT_DATE@", "//SapZodsquoteZ2odsstatMain0001409007221/EvtDate");
        xpathMap.put("@Doc_EVENT_TIME@", "//SapZodsquoteZ2odsstatMain0001409007221/EvtTime");

        Map<String, Object> map = new HashMap<>();
        WmqUtil.addEleTextMapByEleXpathMap(map, testPayload, xpathMap);
        return WmqUtil.replaceInFile(templateFile, map);
    }
}
