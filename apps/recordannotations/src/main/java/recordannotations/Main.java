/*
 * Copyright (c) 2022, Red Hat Inc. All rights reserved.
 *
 * See the NOTICE file(s) distributed with this work for additional
 * information regarding copyright ownership.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * You may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 */
package recordannotations;

import java.lang.annotation.Annotation;
import java.lang.reflect.RecordComponent;
import java.lang.reflect.AnnotatedType;
import java.lang.reflect.Type;
import java.util.Arrays;

/**
 * @author Severin Gehwolf <sgehwolf@redhat.com>
 */
public class Main {

	public static void main(String[] args) {
		RecordComponent[] recordComponents = F.class.getRecordComponents();
		for (RecordComponent component: recordComponents) {
			System.out.println("component: " + component);
			Annotation[] annotations = component.getAnnotations();
			AnnotatedType annoType = component.getAnnotatedType();
			Type t = component.getGenericType();
			System.out.println("generic type: " + t);
			System.out.println("annotated type: " + annoType);
			System.out.println("annotated type annotations: " + Arrays.asList(annoType.getAnnotations()));
			for (Annotation annotation : annotations) {
				System.out.println("annotation: " + annotation);
			}
			Annotation ann = component.getAnnotation(RCA.class);
			System.out.println("RCA annotation: " + ann);
		}

		F x = new F("x", 1);
		System.out.println("Record: " + x);
	}

}
