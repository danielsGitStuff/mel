package de.mein.android.drive.data;

import android.database.Cursor;

import de.mein.auth.tools.N;

public class NC {
    public interface CursorHandler {

        void handle(Cursor cursor, N.Stoppable stoppable);


    }

    public static void iterate(Cursor c, CursorHandler handler) {
        try {
            N.Stoppable stoppable = new N.Stoppable();
            while (c.moveToNext()) {
                handler.handle(c, stoppable);
                if (stoppable.isStopped())
                    break;
            }
        } finally {
            c.close();
        }
    }
}
