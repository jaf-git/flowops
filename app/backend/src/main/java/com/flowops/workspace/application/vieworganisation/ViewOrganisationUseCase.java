package com.flowops.workspace.application.vieworganisation;

import com.flowops.workspace.domain.model.Department;
import java.util.List;

public interface ViewOrganisationUseCase {
    List<Department> execute();
}
