package org.jetlinks.collector;

import jakarta.annotation.Nonnull;
import org.jetlinks.core.Wrapper;

/**
 * 点位描述符号
 * <p>
 * 请实现 {@link Object#equals(Object)}和{@link Object#hashCode()}方法来实现点位唯一性
 *
 * @author zhouhao
 * @since 1.0.1
 */
public interface PointDescriptor extends Wrapper {


    @Nonnull
    PointMetadata metadata();


}
