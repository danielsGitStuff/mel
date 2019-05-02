package de.mein.lok;

import de.mein.Tokenizer;
import org.junit.Before;
import org.junit.Test;

import java.util.List;

public class TokenTest {
    private Tokenizer tokenizer;
    private List<String> result;

    @Before
    public void before() {
        tokenizer = new Tokenizer();
        result = null;
    }

    @Test
    public void simple() {
        result = tokenizer.tokenize("simple");
    }

    @Test
    public void complex() {
        result = tokenizer.tokenize("notify-send   \"c \\\"d e\"  keks \"a b c\" x \" Y \"");
    }
}
