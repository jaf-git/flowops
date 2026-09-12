package com.flowops.tasklib.infrastructure.task;

import com.flowops.task.application.viewtaskstate.DescribeTaskStateUseCase;
import com.flowops.tasklib.application.port.DescribeTaskPort;
import java.util.List;
import java.util.Optional;
import java.util.UUID;
import org.springframework.stereotype.Component;

@Component
public class DescribeTaskAdapter implements DescribeTaskPort {
    private final DescribeTaskStateUseCase describeTaskStateUseCase;

    public DescribeTaskAdapter(DescribeTaskStateUseCase describeTaskStateUseCase) {
        this.describeTaskStateUseCase = describeTaskStateUseCase;
    }

    @Override
    public Optional<Words> describe(UUID task) {
        List<DescribeTaskStateUseCase.TaskSnapshot> found = describeTaskStateUseCase.describe(List.of(task));
        if (found.isEmpty()) {
            return Optional.empty();
        }
        DescribeTaskStateUseCase.TaskSnapshot snapshot = found.get(0);
        return Optional.of(new Words(snapshot.title(), snapshot.description(), snapshot.priority()));
    }
}
