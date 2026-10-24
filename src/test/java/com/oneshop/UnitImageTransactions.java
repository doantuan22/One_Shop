package com.oneshop;

import com.oneshop.service.CloudinaryService;
import com.oneshop.service.image.ImageCleanupService;
import com.oneshop.service.image.ImageTransactionCoordinator;
import org.springframework.aop.framework.ProxyFactory;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.annotation.AnnotationTransactionAttributeSource;
import org.springframework.transaction.interceptor.TransactionInterceptor;
import org.springframework.transaction.support.AbstractPlatformTransactionManager;
import org.springframework.transaction.support.DefaultTransactionStatus;

import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

/** Unit tests use real Spring commit/rollback callbacks but no SQL/provider. SQL tests use the real beans. */
public final class UnitImageTransactions {
    private UnitImageTransactions() { }
    public static ImageTransactionCoordinator coordinator(CloudinaryService cloudinary) {
        ImageCleanupService cleanup=mock(ImageCleanupService.class);
        Map<UUID,String> tasks=new ConcurrentHashMap<>();
        lenient().when(cleanup.schedule(anyString())).thenAnswer(i->{UUID id=UUID.randomUUID();tasks.put(id,i.getArgument(0));return id;});
        lenient().doAnswer(i->{cloudinary.deleteImage(tasks.remove(i.getArgument(0)));return null;}).when(cleanup).process(any());
        lenient().doAnswer(i->{cloudinary.deleteImage(i.getArgument(0));return null;}).when(cleanup).compensate(anyString());
        return new ImageTransactionCoordinator(cloudinary,cleanup);
    }
    @SuppressWarnings("unchecked") public static <T> T transactional(T target) {
        var manager=new AbstractPlatformTransactionManager() {
            @Override protected Object doGetTransaction(){return new Object();}
            @Override protected void doBegin(Object tx, TransactionDefinition definition) { }
            @Override protected void doCommit(DefaultTransactionStatus status) { }
            @Override protected void doRollback(DefaultTransactionStatus status) { }
        };
        ProxyFactory proxy=new ProxyFactory(target);
        proxy.addAdvice(new TransactionInterceptor(manager,new AnnotationTransactionAttributeSource()));
        return (T)proxy.getProxy();
    }
}
