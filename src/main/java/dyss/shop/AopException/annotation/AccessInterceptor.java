package dyss.shop.AopException.annotation;

import org.aspectj.lang.annotation.Pointcut;

import java.lang.annotation.*;

@Documented
@Retention(RetentionPolicy.RUNTIME)
@Target({ElementType.TYPE,ElementType.METHOD})
public @interface AccessInterceptor {
    // 哪个字段作为拦截表示？如果没有配置就走全部
    String key() default "all";
    // 限制频次
    double permitsPerSecond();
    // 黑名单拦截 多少次后进入黑名单
    double blackListCount() default 0;
    // 拦截后执行方法
    String fallbackMethod();
}




