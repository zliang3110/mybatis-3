/**
 *    Copyright 2009-2020 the original author or authors.
 *
 *    Licensed under the Apache License, Version 2.0 (the "License");
 *    you may not use this file except in compliance with the License.
 *    You may obtain a copy of the License at
 *
 *       http://www.apache.org/licenses/LICENSE-2.0
 *
 *    Unless required by applicable law or agreed to in writing, software
 *    distributed under the License is distributed on an "AS IS" BASIS,
 *    WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *    See the License for the specific language governing permissions and
 *    limitations under the License.
 */
package org.apache.ibatis.reflection;

import java.lang.annotation.Annotation;
import java.lang.reflect.Method;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.SortedMap;
import java.util.TreeMap;

import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.binding.MapperMethod.ParamMap;
import org.apache.ibatis.session.Configuration;
import org.apache.ibatis.session.ResultHandler;
import org.apache.ibatis.session.RowBounds;

public class ParamNameResolver {

  public static final String GENERIC_NAME_PREFIX = "param";

  private final boolean useActualParamName;

  /**
   * <p>
   * The key is the index and the value is the name of the parameter.<br />
   * The name is obtained from {@link Param} if specified. When {@link Param} is not specified,
   * the parameter index is used. Note that this index could be different from the actual index
   * when the method has special parameters (i.e. {@link RowBounds} or {@link ResultHandler}).
   * </p>
   * <ul>
   * <li>aMethod(@Param("M") int a, @Param("N") int b) -&gt; {{0, "M"}, {1, "N"}}</li>
   * <li>aMethod(int a, int b) -&gt; {{0, "0"}, {1, "1"}}</li>
   * <li>aMethod(int a, RowBounds rb, int b) -&gt; {{0, "0"}, {2, "1"}}</li>
   * </ul>
   */
  private final SortedMap<Integer, String> names;

  private boolean hasParamAnnotation;

  public ParamNameResolver(Configuration config, Method method) {
    /*
    当你在处理一个带有多个形参的构造方法时，很容易搞乱 arg 元素的顺序。
    从版本 3.4.3 开始，可以在指定参数名称的前提下，以任意顺序编写 arg 元素。
    为了通过名称来引用构造方法参数，你可以添加 @Param 注解，
    或者使用 '-parameters' 编译选项并启用 useActualParamName 选项（默认开启）来编译项目
     */
    this.useActualParamName = config.isUseActualParamName();
    // 获取参数类型列表
    final Class<?>[] paramTypes = method.getParameterTypes();
    // 获取参数注解
    final Annotation[][] paramAnnotations = method.getParameterAnnotations();
    final SortedMap<Integer, String> map = new TreeMap<>();
    int paramCount = paramAnnotations.length;
    // get names from @Param annotations
    for (int paramIndex = 0; paramIndex < paramCount; paramIndex++) {
      // 检测当前的参数类型是否为 RowBounds 或 ResultHandler
      if (isSpecialParameter(paramTypes[paramIndex])) {
        // skip special parameters
        //跳过
        continue;
      }
      String name = null;
      for (Annotation annotation : paramAnnotations[paramIndex]) {
        if (annotation instanceof Param) {
          hasParamAnnotation = true;
          // 获取 @Param 注解内容
          name = ((Param) annotation).value();
          break;
        }
      }
      // name 为空，表明未给参数配置 @Param 注解
      if (name == null) {
        // @Param was not specified.
        if (useActualParamName) {
          // 通过反射获取参数名称。此种方式要求 JDK 版本为 1.8+，
          // 且要求编译时加入 -parameters 参数，否则获取到的参数名
          // 仍然是 arg1, arg2, ..., argN
          name = getActualParamName(method, paramIndex);
        }
        if (name == null) {
          // use the parameter index as the name ("0", "1", ...)
          // gcode issue #71
          /*
          * 使用 map.size() 返回值作为名称，思考一下为什么不这样写：
          * name = String.valueOf(paramIndex);
          * 因为如果参数列表中包含 RowBounds 或 ResultHandler，这两个
          * 参数会被忽略掉，这样将导致名称不连续。
          *
          * 比如参数列表 (int p1, int p2, RowBounds rb, int p3)
          * - 期望得到名称列表为 ["0", "1", "2"]
          * - 实际得到名称列表为 ["0", "1", "3"]
          */
          name = String.valueOf(map.size());
        }
      }
      // 存储 paramIndex 到 name 的映射
      map.put(paramIndex, name);
    }
    names = Collections.unmodifiableSortedMap(map);
  }

  private String getActualParamName(Method method, int paramIndex) {
    return ParamNameUtil.getParamNames(method).get(paramIndex);
  }

  private static boolean isSpecialParameter(Class<?> clazz) {
    return RowBounds.class.isAssignableFrom(clazz) || ResultHandler.class.isAssignableFrom(clazz);
  }

  /**
   * Returns parameter names referenced by SQL providers.
   *
   * @return the names
   */
  public String[] getNames() {
    return names.values().toArray(new String[0]);
  }

  /**
   * <p>
   * A single non-special parameter is returned without a name.
   * Multiple parameters are named using the naming rule.
   * In addition to the default names, this method also adds the generic names (param1, param2,
   * ...).
   * </p>
   *
   * @param args
   *          the args
   * @return the named params
   */
  public Object getNamedParams(Object[] args) {
    final int paramCount = names.size();
    if (args == null || paramCount == 0) {
      return null;
    } else if (!hasParamAnnotation && paramCount == 1) {
      /*
      * 如果方法参数列表无 @Param 注解，且仅有一个非特别参数，则返回该
      * 参数的值。比如如下方法：
      * List findList(RowBounds rb, String name)
      * names 如下：
      * names = {1 : "0"}
      * 此种情况下，返回 args[names.firstKey()]，即 args[1] -> name
      */
      Object value = args[names.firstKey()];
      return wrapToMapIfCollection(value, useActualParamName ? names.get(0) : null);
    } else {
      // 添加 <参数名, 参数值> 键值对到 param 中
      final Map<String, Object> param = new ParamMap<>();
      int i = 0;
      for (Map.Entry<Integer, String> entry : names.entrySet()) {
        param.put(entry.getValue(), args[entry.getKey()]);
        // add generic param names (param1, param2, ...)
        // genericParamName = param + index。比如 param1, param2,... paramN
        final String genericParamName = GENERIC_NAME_PREFIX + (i + 1);
        // ensure not to overwrite parameter named with @Param
        // 检测 names 中是否包含 genericParamName，什么情况下会包含？
        // 答案如下：
        // 使用者显式将参数名称配置为 param1，即 @Param("param1")
        if (!names.containsValue(genericParamName)) {
          // 添加 <param*, value> 到 param 中
          param.put(genericParamName, args[entry.getKey()]);
        }
        i++;
      }
      return param;
    }
  }

  /**
   * Wrap to a {@link ParamMap} if object is {@link Collection} or array.
   *
   * @param object a parameter object
   * @param actualParamName an actual parameter name
   *                        (If specify a name, set an object to {@link ParamMap} with specified name)
   * @return a {@link ParamMap}
   * @since 3.5.5
   */
  public static Object wrapToMapIfCollection(Object object, String actualParamName) {
    if (object instanceof Collection) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("collection", object);
      if (object instanceof List) {
        map.put("list", object);
      }
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    } else if (object != null && object.getClass().isArray()) {
      ParamMap<Object> map = new ParamMap<>();
      map.put("array", object);
      Optional.ofNullable(actualParamName).ifPresent(name -> map.put(name, object));
      return map;
    }
    return object;
  }

}
