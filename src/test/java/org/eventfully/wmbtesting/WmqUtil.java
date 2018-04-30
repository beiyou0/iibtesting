package org.eventfully.wmbtesting;

import org.dom4j.*;
import org.dom4j.io.SAXReader;

import java.io.*;
import java.math.BigDecimal;
import java.math.BigInteger;
import java.sql.*;
import java.sql.Date;
import java.util.*;

/**
 * Created by qianqian on 23/05/2017.
 */
public class WmqUtil {

//    IBM MQ algorithm for JMSCorrelationID with technote link: http://www-01.ibm.com/support/docview.wss?uid=swg21569646
    public static String getHexString(byte[] b) throws Exception {
        String result = "";
        for (int i=0; i < b.length; i++) {
            result += Integer.toString( ( b[i] & 0xff ) + 0x100, 16).substring( 1 );
        }
        return result;
    }

    public static byte[] getRandomByte24NumArray() {
        BigInteger bigInt = new BigInteger(UUID.randomUUID().toString().replace("-",""), 16);
        byte[] b = bigInt.toString().substring(0, 24).getBytes();
        return b;
    }

    public static void addOneToXMLAndDBMapByEleName(Map<String, Element> map, File xmlFile,
                                                     String propFile, String eleName, String prefix) throws IOException, DocumentException {
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        SAXReader sax = new SAXReader();
        Element root = sax.read(xmlFile).getRootElement();
        Element one = root.element(eleName);

        String content = prop.getProperty(prefix + eleName);
        if (content != null) {
            String text = dataMappingToString(content, one.getText());
            one.setText(text);
            map.put(content.split(",")[0], one);
        }
    }

    public static void addOneToXMLAndDBMapByEleXpath(Map<String, Element> map, File xmlFile,
                                                     String propFile, String eleXpath, String eleName, String prefix) throws IOException, DocumentException {
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        Element one = (Element) doc.selectSingleNode(eleXpath);

        String content = prop.getProperty(prefix + eleName);
        if (content != null) {
            String text = dataMappingToString(content, one.getText());
            one.setText(text);
            map.put(content.split(",")[0], one);
        }
    }

    public static void addAllToXMLAndDBMapByParentName(Map<String, Element> map, File xmlFile, String propFile,
                                               String parentEleStr, String prefix) throws DocumentException, IOException {
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        SAXReader sax = new SAXReader();
        Element root = sax.read(xmlFile).getRootElement();
        Element parentEle = root.element(prop.getProperty(parentEleStr));
        List<Element> list = parentEle.elements();
        for(Element e : list) {
            String content = prop.getProperty(prefix + e.getName());
            if (content != null) {
                String text = dataMappingToString(content, e.getText());
                e.setText(text);
                map.put(content.split(",")[0], e);
            }
        }
    }

    public static void addAllToXMLAndDBMapByParentXpath(Map<String, Element> map, File xmlFile, String propFile,
                                                            String parXpath, String prefix) throws IOException, DocumentException {
        Properties prop = new Properties();
        FileInputStream in = new FileInputStream(propFile);
        prop.load(in);
        in.close();

        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        List list = doc.selectNodes(parXpath + "/child::*");
        for(Object o : list) {
            Element e = (Element) o;
            String content = prop.getProperty(prefix + e.getName());
            if (content != null) {
                String text = dataMappingToString(content, e.getText());
                e.setText(text);
                map.put(content.split(",")[0], e);
            }
        }
    }

    private static String dataMappingToString(String content, String data) {
        // content is string pattern as: Header001.DocTaxAmt=DOC_TAX_AMT,decimal,4 in xxTableMap.properties file
        String input = data.trim();
        String output = input;
        String type = content.split(",")[1];
        switch (type) {
            case "char":
                if (content.split(",").length == 3 && content.split(",")[2].equals("upper")) {
                    output = output.toUpperCase();
                }
                break;
            case "varchar":
                if (content.split(",").length == 3 && content.split(",")[2].equals("upper")) {
                    output = output.toUpperCase();
                }
                break;
            case "smallint":
                Integer smi = Integer.valueOf(input);
                output = smi.toString();
                break;
            case "integer":
                Integer it = Integer.valueOf(input);
                output = it.toString();
                break;
            case "decimal":
                String digital = content.split(",")[2];
                BigDecimal bd = new BigDecimal(input);
                bd = bd.setScale(Integer.parseInt(digital));
                output = bd.toString();
                break;
            case "date":
                int dateStyle = Integer.parseInt(content.split(",")[2]);
                switch (dateStyle) {
                    case 3:
                        // dateStyle 3: 05/12/2017 or 05/12/2017 00:00
                        String dateFormat3 = input.substring(6, 10) + "-" + input.substring(0, 2) + "-" + input.substring(3, 5);
                        Date sqlDate3 = Date.valueOf(dateFormat3);
                        output = sqlDate3.toString();
                        break;
                    case 4:
                        // dateStyle 4: 20170622
                        String dateFormat4 = input.substring(0, 4) + "-" + input.substring(4, 6) + "-" + input.substring(6, 8);
                        Date sqlDate4 = Date.valueOf(dateFormat4);
                        output = sqlDate4.toString();
                        break;
                    default:
                        System.out.println("Date style in date datatype is not supported.");
                }
                break;
            case "timestamp":
                int tsStyle = Integer.parseInt(content.split(",")[2]);
                switch (tsStyle) {
                    case 1:
                        // timestamp format in xml: 05/12/2017 03:11 or 05/15/2017 23:03:10
                        String[] timeArray = input.split(" ");
                        String[] ymdArray = timeArray[0].split("/");
                        String timestamp = ymdArray[2] + "-" + ymdArray[0] + "-" + ymdArray[1] + " " + timeArray[1];
                        if (timeArray[1].split(":").length == 2) {
                            timestamp = timestamp + ":00";
                        }
                        Timestamp sqlTimeStamp = Timestamp.valueOf(timestamp);
                        output = sqlTimeStamp.toString();
                        break;
                    case 2:
                        break;
                    default:
                        System.out.println("Timestamp style in date datatype is not supported.");
                }
                break;
            default:
                System.out.println("DataType is not supported.");
        }
        return output;
    }

    public static int getCountOfElementByName(File xmlFile, String name) throws DocumentException {
        int count;
        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        count = doc.numberValueOf("count(//" + name + ")").intValue();
        return count;
    }

    public static void addEleTextMapByEleXpathMap(Map<String, Object> map, File xmlFile, Map<String, String> xpathMap) throws DocumentException {
        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        for (String key: xpathMap.keySet()) {
            String text = doc.valueOf(xpathMap.get(key));
            map.put(key, text);
        }
    }

    public static boolean isEleExistByXpath(File xmlFile, String xpath) throws DocumentException {
        boolean isExist = false;
        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        if(doc.selectSingleNode(xpath) != null) {
            isExist = true;
        }
        return isExist;
    }

    public static String getEleContextByXpath(File xmlFile, String xpath) throws DocumentException {
        String context;
        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        Element ele = (Element) doc.selectSingleNode(xpath);
        context = ele.getTextTrim();
        return context;
    }

    public static void addEleTextMapByEleXpathString(Map<String, Object> map, File xmlFile, String key, String xpath) throws DocumentException {
        SAXReader sax = new SAXReader();
        Document doc = sax.read(xmlFile);
        String text = doc.valueOf(xpath);
        map.put(key, text);
    }

    public static String replaceInFile(File file, Map<String, Object> map) throws IOException {
        BufferedReader br = new BufferedReader(new FileReader(file));
        String output = "";
        String tmp = br.readLine();
        while (tmp != null) {
            if (!(tmp.startsWith("#") || tmp.startsWith("@rem"))) {
                for (String key: map.keySet()) {
                    tmp = tmp.replace(key, (String) map.get(key));
                }
            }
            output += tmp.trim();
            tmp = br.readLine();
        }
        return output;
    }

    // add for new feature which handling SO and BG time issue - US1342588
    public static String jmsCorrelationIdOfRTFlow(String indexStr) {
        String stdStr = "000000000000000000000000000000000000000000000000";
        return indexStr + stdStr.substring(indexStr.length());
    }

//    public static void main(String[] args) {
//
//    }
}
