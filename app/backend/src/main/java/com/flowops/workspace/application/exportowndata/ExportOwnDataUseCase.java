package com.flowops.workspace.application.exportowndata;

import com.flowops.workspace.application.viewowndata.ViewOwnDataQuery;
import com.flowops.workspace.application.viewowndata.ViewOwnDataResult;

public interface ExportOwnDataUseCase {
    ViewOwnDataResult execute(ViewOwnDataQuery query);
}
