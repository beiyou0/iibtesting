import org.apache.camel.CamelContext;
import org.apache.camel.builder.RouteBuilder;
import org.apache.camel.impl.DefaultCamelContext;

/**
 * Created by qianqian on 18/08/2017.
 */
public class FileMoveTest {
    public static void main(String args[]) throws Exception {
        CamelContext context = new DefaultCamelContext();
        context.addRoutes(new RouteBuilder() {
            @Override
            public void configure() throws Exception {
                from("file:/Users/qianqian/work/test/source?delay=30000").to("file:/Users/qianqian/work/test/target");

                FileConvertProcessor processor = new FileConvertProcessor();
                // file:/Users/qianqian/work/test/source2?noop=true
                from("file:/Users/qianqian/work/test/source2?delay=20000").process(processor).to("file:/Users/qianqian/work/test/target2");
            }
        });
        context.start();
        int loop = 0;
        while (loop <= 2) {
            Thread.sleep(25000);
            loop++;
        }
        context.stop();
    }
}
