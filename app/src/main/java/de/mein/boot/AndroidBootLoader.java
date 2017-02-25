package de.mein.boot;

import de.mein.auth.service.IMeinService;

/**
 * Created by xor on 2/25/17.
 */

public interface AndroidBootLoader<T extends IMeinService> {

    String getCreateResource();
    String getEditResource(T service);
}
