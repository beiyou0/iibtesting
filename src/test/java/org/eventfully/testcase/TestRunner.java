package org.eventfully.testcase;

import org.junit.runner.JUnitCore;
import org.junit.runner.Result;
import org.junit.runner.notification.Failure;

/**
 * Created by qianqian on 28/08/2017.
 */
public class TestRunner {
    public static void main(String args[]) {
        Result result = JUnitCore.runClasses(TestSuite.class);
        System.out.println("\n\nResults Summary:");

        for (Failure failure : result.getFailures()) {
            System.out.println(failure.toString() + "\n");
        }
        System.out.println("Total runtime: " + result.getRunTime() + " milliseconds");

        if (result.wasSuccessful())
            System.out.println("All " + result.getRunCount() + " test cases have passed.");
        else
            System.out.println(result.getFailureCount() + " of " + result.getRunCount() + " test cases have failed.");
    }
}
