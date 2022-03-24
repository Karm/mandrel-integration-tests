package recordannotations;

import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import java.lang.annotation.ElementType;

@Retention(RetentionPolicy.RUNTIME)
@Target({ ElementType.RECORD_COMPONENT, ElementType.FIELD })
@interface RCA {
  // empty
}

