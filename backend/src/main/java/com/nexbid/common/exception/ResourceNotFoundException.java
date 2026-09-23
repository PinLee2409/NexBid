package com.nexbid.common.exception;

/**
 * EN: The record referenced by id does not exist. Same behaviour as its parent, clearer at the call site.
 * VI: Bản ghi theo id không tồn tại. Hành vi y hệt lớp cha, chỉ để đọc rõ nghĩa hơn ở nơi gọi.
 */
public class ResourceNotFoundException extends BusinessException {

    public ResourceNotFoundException(ErrorCode code, String message) {
        super(code, message);
    }

    public ResourceNotFoundException(ErrorCode code) {
        super(code);
    }
}
