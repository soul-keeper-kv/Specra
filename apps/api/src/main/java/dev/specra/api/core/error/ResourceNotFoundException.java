package dev.specra.api.core.error;

import dev.specra.api.core.i18n.LocalizedText;

/**
 * Thrown when a lookup by id finds nothing. The resource name is itself a translation key, so "Note
 * 7f3c… not found" and "Không tìm thấy ghi chú 7f3c…" come out of the same throw site.
 */
public class ResourceNotFoundException extends BusinessException {

  public ResourceNotFoundException(String resourceKey, Object id) {
    super(
        ErrorCode.RESOURCE_NOT_FOUND,
        ErrorCode.RESOURCE_NOT_FOUND.detailKey(),
        new LocalizedText(resourceKey),
        id);
  }
}
