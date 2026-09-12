package com.flowops.process.application.viewinstance;

import com.flowops.process.domain.model.InstanceId;
import java.util.List;

public interface ViewInstanceUseCase {
    List<InstanceView> visible(InstancePopulation population);

    InstanceView one(InstanceId id);
}
