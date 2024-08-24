package dyss.shop.AopException.aop;

import com.google.common.cache.Cache;
import com.google.common.cache.CacheBuilder;
import com.google.common.util.concurrent.RateLimiter;
import dyss.shop.AopException.annotation.AccessInterceptor;
import lombok.extern.slf4j.Slf4j;
import org.apache.commons.lang3.StringUtils;
import org.aspectj.lang.JoinPoint;
import org.aspectj.lang.ProceedingJoinPoint;
import org.aspectj.lang.Signature;
import org.aspectj.lang.annotation.Around;
import org.aspectj.lang.annotation.Aspect;
import org.aspectj.lang.annotation.Pointcut;
import org.aspectj.lang.reflect.MethodSignature;

import java.lang.reflect.Field;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.TimeUnit;

/**
 * @author DYss东阳书生
 * @date 2024/8/24 11:16
 * @Description 描述
 */
@Slf4j
@Aspect
public class RateLimiterAOP {
    //个人限频一分钟
    private final Cache<String, RateLimiter> loginRecord=CacheBuilder.newBuilder().
            expireAfterWrite(1, TimeUnit.MINUTES).build();
    //限频24H，黑名单
    private final Cache<String, Long> blacklist = CacheBuilder.newBuilder()
            .expireAfterWrite(24, TimeUnit.HOURS)
            .build();

    //指定切点表达式，只要有目标标签，则符合
    @Pointcut("@annotation(dyss.shop.AopException.annotation.AccessInterceptor)")
    public void aopPoint() {
    }

    //指定要拦截的对象为具有标签的类
    @Around("aopPoint() && @annotation(accessInterceptor)")
    public Object doRouter(ProceedingJoinPoint jp, AccessInterceptor accessInterceptor) throws Throwable {
        String key = accessInterceptor.key();
        //看key是否异常，key是判断要拦截的是哪个字段，否则全部一起看
        if (StringUtils.isBlank(key)) {
            throw new RuntimeException("annotation RateLimiter uId is null！");
        }
        //获取要拦截的参数  看是否传过来的参数也有
        String keyAttr = getAttrValue(key, jp.getArgs());
        log.info("aop attr {}", keyAttr);

        //黑名单拦截  只要我本地黑名单上的数字，大于标签上的数字就触发黑名单
        if(!"all".equals(keyAttr) && accessInterceptor.blackListCount() !=0 && blacklist.getIfPresent(keyAttr)!=null
        && blacklist.getIfPresent(keyAttr) > accessInterceptor.blackListCount()){
            log.info("限流-黑名单拦截(24h):{}",keyAttr);
            //调用黑名单方法
            return fallbackMethodResult(jp,accessInterceptor.fallbackMethod());
        }
        //获得限流对象
        RateLimiter rateLimiter = loginRecord.getIfPresent(keyAttr);
        //不存在则进行缓存
        if (rateLimiter==null) {
            rateLimiter = RateLimiter.create(accessInterceptor.permitsPerSecond());
            loginRecord.put(keyAttr,rateLimiter);
        }

        //存在限流对象则，限流拦截    如果不能得到许可，也就是超过了每秒令牌桶算法产生的数量，也就是黑名单行为
        if(!rateLimiter.tryAcquire()){
            //看看有没有黑名单限流
            if (accessInterceptor.blackListCount()!=0){
                //次数+1
                if (blacklist.getIfPresent(keyAttr)==null) {
                    blacklist.put(keyAttr,1L);
                }else{
                    blacklist.put(keyAttr, blacklist.getIfPresent(keyAttr) + 1L);
                }
            }
            log.info("限流-超频次拦截：{}", keyAttr);
            return fallbackMethodResult(jp, accessInterceptor.fallbackMethod());
        }
        //执行切点方法
        return jp.proceed();
    }

    private Object fallbackMethodResult(JoinPoint jp, String fallbackMethod) throws NoSuchMethodException, InvocationTargetException, IllegalAccessException {
        MethodSignature methodSignature = (MethodSignature) jp.getSignature();
        Method method =jp.getTarget().getClass().getMethod(fallbackMethod,methodSignature.getParameterTypes());
        return method.invoke(jp.getThis(),jp.getArgs());
    }

    public String getAttrValue(String attr, Object[] args) {
        if (args[0] instanceof String) {
            return args[0].toString();
        }
        String filedValue = null;
        for (Object arg : args) {
            try {
                if (StringUtils.isNotBlank(filedValue)) {
                    break;
                }
                // filedValue = BeanUtils.getProperty(arg, attr);
                // fix: 使用lombok时，uId这种字段的get方法与idea生成的get方法不同，会导致获取不到属性值，改成反射获取解决
                filedValue = String.valueOf(this.getValueByName(arg, attr));
            } catch (Exception e) {
                log.error("获取路由属性值失败 attr：{}", attr, e);
            }
        }
        return filedValue;
    }

    /**
     * 获取对象的特定属性值
     *
     * @param item 对象
     * @param name 属性名
     * @return 属性值
     * @author tang
     */
    private Object getValueByName(Object item, String name) {
        try {
            Field field = getFieldByName(item, name);
            if (field == null) {
                //如果没有该属性，返回空
                return null;
            }
           //为了获取私有属性，必须设置
            field.setAccessible(true);
            Object o = field.get(item);
            //为了维护封装性，使外界不可显示访问，故而设置回去
            field.setAccessible(false);
            return o;
        } catch (IllegalAccessException e) {
            return null;
        }
    }

    /**
     * 根据名称获取方法，该方法同时兼顾继承类获取父类的属性
     *
     * @param item 对象
     * @param name 属性名
     * @return 该属性对应方法
     * @author tang
     */
    private Field getFieldByName(Object item, String name) {
        try {
            Field field;
            try {
                //declared获取不到继承下来的属性，所以往其父类走
                field = item.getClass().getDeclaredField(name);
            } catch (NoSuchFieldException e) {
                //如果根据超类【object】也获取不到，那么就是不存在
                field = item.getClass().getSuperclass().getDeclaredField(name);
            }
            return field;
        } catch (NoSuchFieldException e) {
            return null;
        }
    }



}
