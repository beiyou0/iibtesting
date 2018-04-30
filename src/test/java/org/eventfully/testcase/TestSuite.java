package org.eventfully.testcase;

import org.junit.runner.RunWith;
import org.junit.runners.Suite;

/**
 * Created by qianqian on 28/08/2017.
 */
@RunWith(Suite.class)
@Suite.SuiteClasses({
        HttpJsonRequestTest.class,
//        RightTimeSOTest.class,
        RightTimeBGTest.class,
//        RightTimeQUTest.class,
        WmqRequestReplyTest.class
})
public class TestSuite {
}
