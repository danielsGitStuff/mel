package de.mein;

import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

public class Tokenizer {
    public static void main(String[] args) {
        Tokenizer tokenizer = new Tokenizer();
        String string = "notify-send   \"c \\\"d e\"  keks \"a b c\" x \" Y \"";
        List<String> tokens = tokenizer.tokenize(string);
        if (tokens != null)
            for (String token : tokens)
                System.out.println(token + "\n");
    }


    public List<String> tokenize(String string) {
        Lok.debug("tokenizing: " + string);
        boolean inQuote = false;
        List<String> tokens = new ArrayList<>();
        StringBuilder builder = new StringBuilder();
        int count = 0;
        StringTokenizer stringTokenizer = new StringTokenizer(string, " ", true);
        while (stringTokenizer.hasMoreTokens()) {
            String token = stringTokenizer.nextToken();
            Lok.debug("t[" + count + "]: " + token);
            if (count == 21)
                Lok.debug("lel");
            if (token.startsWith("\"")) {
                Lok.debug("quote started");
                builder = new StringBuilder();
                String part = token.substring(1);
                builder.append(token);
                inQuote = true;
            } else if (inQuote) {
                builder.append(token);
                if (token.endsWith("\"")) {
                    Lok.debug("checking if quote ends");
                    // count backslashes backwards
                    int offset = 0;
                    Character c = token.charAt(token.length() - 1 - offset);
                    while (c.equals('\\')) {
                        offset--;
                        c = token.charAt(token.length() - 1 - offset);
                    }
                    if (offset % 2 == 0) {
                        inQuote = false;
                        tokens.add(builder.toString());
                        Lok.debug("quote ended");
                    }
                }
            } else if (!token.trim().isEmpty()) {
                tokens.add(token);
            }
            count++;
        }
        return tokens;
    }
}
