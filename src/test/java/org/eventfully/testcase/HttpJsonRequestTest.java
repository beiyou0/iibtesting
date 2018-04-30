package org.eventfully.testcase;

import com.google.gson.JsonArray;
import com.google.gson.JsonObject;
import com.google.gson.JsonParser;
import org.apache.camel.CamelContext;
import org.apache.camel.Exchange;
import org.apache.camel.ProducerTemplate;
import org.eventfully.wmbtesting.EmptyRouteCamelConfiguration;
import org.junit.*;
import org.junit.runner.RunWith;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.context.junit4.SpringJUnit4ClassRunner;

import java.io.File;
import java.util.HashMap;
import java.util.Map;

import static junit.framework.Assert.assertNotNull;
import static org.junit.Assert.assertTrue;

/**
 * Created by qianqian on 26/05/2017.
 */
@RunWith(SpringJUnit4ClassRunner.class)
@ContextConfiguration(classes = EmptyRouteCamelConfiguration.class)
public class HttpJsonRequestTest {
    private static final Logger logger = LoggerFactory.getLogger(HttpJsonRequestTest.class);

    @Autowired
    public CamelContext camelContext;

    public ProducerTemplate producer;


    @Before
    public void setUp() {
        producer = camelContext.createProducerTemplate();
    }

    @After
    public void tearDown() {
        producer = null;
    }

    @Test
//    @Ignore
    public void httpJsonRequestGetTest() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "GET");

        String reply = producer.requestBodyAndHeaders("https4:sbybz2070.sby.ibm.com:7084/jsonrequest*", "", headers, String.class);
        assertNotNull(reply);

        JsonParser parser = new JsonParser();
        JsonArray array = (JsonArray) parser.parse(reply);
        for (int i = 0; i < array.size(); i++) {
            JsonObject subObj = array.get(i).getAsJsonObject();
            logger.info("Title: " + subObj.get("Title").getAsString() + " | " + "Artist: " + subObj.get("Artist").getAsString()
                         + "Artist: " + subObj.get("Artist").getAsString() +  " | " +  "Country: " + subObj.get("Country").getAsString()
                         + "Price: " + subObj.get("Price").getAsString() +  " | " + "Year: " + subObj.get("Year").getAsString());

            JsonArray subArray = subObj.get("Members").getAsJsonArray();
            for (int j = 0; j < subArray.size(); j++) {
                String test = subArray.get(j).getAsString();
                logger.info("Members: " + test);
            }
        }
    }

    @Test
//    @Ignore
    public void httpJsonRequestPostTest() {
        File testPayload = new File(this.getClass().getResource("/HttpJsonRest/restPost.json").getFile());

        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "POST");

        String reply = producer.requestBodyAndHeaders("https4:sbybz2070.sby.ibm.com:7084/jsonrequest*", testPayload, headers, String.class);

        assertNotNull(reply);
        logger.info("reply output: " + reply);
    }

//    @Ignore
    @Test
    public void httpJsonRequestDeleteTest() {
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "DELETE");

        String reply = producer.requestBodyAndHeaders("https4:sbybz2070.sby.ibm.com:7084/jsonrequest/Sharks Ride On A Spoon", "", headers, String.class);

        assertNotNull(reply);
        logger.info("reply output: " + reply);
    }

//    @Ignore
    @Test
    public void httpJsonRequestPutTest() {
        File testPayload = new File(this.getClass().getResource("/HttpJsonRest/restPut.json").getFile());
        Map<String, Object> headers = new HashMap<>();
        headers.put(Exchange.HTTP_METHOD, "PUT");

        String reply = producer.requestBodyAndHeaders("https4:sbybz2070.sby.ibm.com:7084/jsonrequest*", testPayload, headers, String.class);
//        assertTrue(false);
        assertNotNull(reply);
        logger.info("reply output: " + reply);
    }
}
