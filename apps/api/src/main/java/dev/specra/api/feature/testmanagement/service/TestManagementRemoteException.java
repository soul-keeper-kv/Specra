package dev.specra.api.feature.testmanagement.service;

import dev.specra.api.core.error.BusinessException;
import dev.specra.api.core.error.ErrorCode;

final class TestManagementRemoteException extends BusinessException {
  TestManagementRemoteException(ErrorCode code, Object... messageArgs) {
    super(code, code.detailKey(), messageArgs);
  }
}
