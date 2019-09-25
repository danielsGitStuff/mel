package mel.de.meldrive;

import android.net.Uri;

import org.junit.Test;

import static org.junit.Assert.*;

/**
 * Example local unit test, which will execute on the development machine (host).
 *
 * @see <a href="http://d.android.com/tools/testing">Testing documentation</a>
 */
public class ExampleUnitTest {
    @Test
    public void addition_isCorrect() throws Exception {
        assertEquals(4, 2 + 2);
    }

    class A {
        String print() {
            return "AA";
        }

        String test() {
            return A.this.print();
        }
    }

    class B extends A {
        @Override
        String print() {
            return "BB";
        }
    }

    @Test
    public void inheritageTest() {
        A a = new A();
        B b = new B();
        System.out.println("ExampleUnitTest.inheritageTest.print: "+a.print()+"..."+b.print());
        System.out.println("ExampleUnitTest.inheritageTest.test: "+a.test()+"..."+b.test());
    }
}