package de.mein.auth.data;

import de.mein.core.serialize.SerializableEntity;
import org.jdeferred.Promise;
import org.jdeferred.impl.DeferredObject;

/**
 * Created by xor on 5/1/16.
 */
public class Dobject extends DeferredObject<SerializableEntity, Exception, Void> {
    protected DobjectAnswerCheck answerCheck;

    public void check(SerializableEntity entity) {
        if (answerCheck != null)
            this.answerCheck.checkAnswer(this, entity);
        else
            resolve(entity);
    }

    @Override
    public Promise<SerializableEntity, Exception, Void> promise() {
        return super.promise();
    }

    public interface DobjectAnswerCheck {
        /**
         * call either resolve() or reject() in here
         *
         * @param dobject
         * @param entity
         */
        void checkAnswer(Dobject dobject, SerializableEntity entity);
    }

    public Dobject setAnwserCheck(DobjectAnswerCheck anserCheck) {
        this.answerCheck = anserCheck;
        return this;
    }
}
