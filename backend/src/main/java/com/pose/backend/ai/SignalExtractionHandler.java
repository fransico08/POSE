package com.pose.backend.ai;

import com.pose.backend.identity.ActorContext;
import org.springframework.stereotype.Component;
import java.util.UUID;

/** Entry point for a future authorized MESSAGE_RECEIVED consumer; no outbox event is emitted here. */
@Component
public class SignalExtractionHandler {
    private final CustomerSignalService signals;
    public SignalExtractionHandler(CustomerSignalService signals) {this.signals=signals;}
    public CustomerSignalService.ExtractionResponse extract(ActorContext actor,String subjectType,UUID subjectId) {
        return signals.extract(actor,subjectType,subjectId);
    }
}
