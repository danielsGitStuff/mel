package de.mein.auth;

/**
 * Created by xor on 08.08.2017.
 */
public class MeinNotification {
    private String title;
    private String text;
    private Object content;

    public MeinNotification(String title, String text) {
        this.text = text;
        this.title = title;
    }

    public String getText() {
        return text;
    }

    public String getTitle() {
        return title;
    }

    public void setContent(Object dataObject) {
        this.content = dataObject;
    }

    public Object getContent() {
        return content;
    }
}
