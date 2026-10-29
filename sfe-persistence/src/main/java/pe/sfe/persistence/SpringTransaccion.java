package pe.sfe.persistence;

import java.util.function.Supplier;
import org.springframework.stereotype.Component;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.support.TransactionTemplate;
import pe.sfe.core.app.Ports;

/** Adaptador del puerto Transaccion: una unica transaccion de BD alrededor de la emision completa. */
@Component
public class SpringTransaccion implements Ports.Transaccion {

    private final TransactionTemplate template;

    public SpringTransaccion(PlatformTransactionManager tm) {
        this.template = new TransactionTemplate(tm);
    }

    @Override
    public <T> T ejecutar(Supplier<T> trabajo) {
        return template.execute(status -> trabajo.get());
    }
}
