package org.jetlinks.collector;

import lombok.AllArgsConstructor;
import org.hswebframework.web.i18n.LocaleUtils;

@AllArgsConstructor
class UndefinedStatusCode implements StatusCode{
    private final long code;

    @Override
    public long getCode() {
        return code;
    }

    @Override
    public boolean isGood() {
        return false;
    }

    @Override
    public boolean isBad() {
        return false;
    }

    @Override
    public boolean isUncertain() {
        return true;
    }

    @Override
    public String getName() {
        return "Undefined_"+Long.toHexString(code);
    }

    @Override
    public String getDescription() {
        return LocaleUtils.resolveMessage("message.collectors.undefined_status_code", Long.toHexString(code));
    }
}
