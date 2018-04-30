import org.boon.Boon;

def jsonEditorOptions = Boon.fromJson(/{
    disable_edit_json: true,
    disable_properties: true,
    no_additional_properties: true,
    disable_collapse: true,
    disable_array_add: true,
    disable_array_delete: true,
    disable_array_reorder: true,
    theme: "bootstrap2",
    iconlib: 
        "fontawesome4", 
        "schema":{{
            "type": "object",
            "title": "Test Configuration",
            "properties": {
                "testcase_set": {
                    "title": "Select Message Flow",
                    "oneOf": [
                        {
                            "type": "object",
                            "title": "Right Time SalesOrder",
                            "properties": {
                                "testcase_SO": {
                                    "title": "Test Case Class: ",
                                    "type": "string",
                                    "default":"RightTimeSOTest",
                                    "readOnly": "true",
                                    "propertyOrder": 1
                                },
                                "data_SO": {
                                    "title": "Data File: ",
                                    "type": "string",
                                    "default": "SO.xml",
                                    "propertyOrder": 2
                                }
                            }
                        },
                        {
                            "type": "object",
                            "title": "Right Time Billing",
                            "properties": {
                                "testcase_BG": {
                                    "title": "Test Case Class: ",
                                    "type": "string",
                                    "default":"RightTimeBGTest",
                                    "readOnly": "true",
                                    "propertyOrder": 1
                                },
                                "data_BG": {
                                    "title": "Data File: ",
                                    "type": "string",
                                    "default": "BG.xml",
                                    "propertyOrder": 2
                                },
                                "data1_BG": {
                                    "title": "Data File (For RT BG, type2 event): ",
                                    "type": "string",
                                    "default": "BG2.xml",
                                    "propertyOrder": 3
                                },
                                "data2_BG": {
                                    "title": "Data File (For RT BG, type2 event, prepare SO event): ",
                                    "type": "string",
                                    "default": "BG2_preSO.xml",
                                    "propertyOrder": 4
                                }
                            }
                        },
                        {
                            "type": "object",
                            "title": "Right Time Quote",
                            "properties": {
                                "testcase_QU": {
                                    "title": "Test Case Class: ",
                                    "type": "string",
                                    "default":"RightTimeQUTest",
                                    "readOnly": "true",
                                    "propertyOrder": 1
                                },
                                "data_QU": {
                                    "title": "Data File: ",
                                    "type": "string",
                                    "default": "QU.xml",
                                    "propertyOrder": 2
                                }
                            }
                        },
                        {
                            "type": "object",
                            "title": "JSON REST Sample",
                            "properties": {
                                "testcase_JRS": {
                                    "title": "Test Case Class: ",
                                    "type": "string",
                                    "default":"HttpJsonRequestTest",
                                    "readOnly": "true",
                                    "propertyOrder": 1
                                }
                            }
                        },
                        {
                            "type": "object",
                            "title": "Request-Reply MQ Sample",
                            "properties": {
                                "testcase_RRMS": {
                                    "title": "Test Case Class: ",
                                    "type": "string",
                                    "default":"WmqRequestReplyTest",
                                    "readOnly": "true",
                                    "propertyOrder": 1
                                }
                            }
                        }
                    ]
                }
            }
        }
    }
}/);

return jsonEditorOptions;