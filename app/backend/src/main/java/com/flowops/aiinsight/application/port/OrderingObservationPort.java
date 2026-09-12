package com.flowops.aiinsight.application.port;

import com.flowops.aiinsight.domain.OrderingPairDerivation.TaskObservation;
import java.util.List;

public interface OrderingObservationPort {
    List<TaskObservation> orderableWork();

    List<TaskObservation> recurringWork();
}
