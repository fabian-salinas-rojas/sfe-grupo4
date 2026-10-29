package pe.sfe.persistence.log;

import ch.qos.logback.classic.pattern.ClassicConverter;
import ch.qos.logback.classic.spi.ILoggingEvent;
import pe.sfe.core.security.LogMasker;

/** %msgm: igual que %msg pero pasando el mensaje por {@link LogMasker}. */
public class MaskedMessageConverter extends ClassicConverter {

    @Override
    public String convert(ILoggingEvent event) {
        return LogMasker.mask(event.getFormattedMessage());
    }
}
