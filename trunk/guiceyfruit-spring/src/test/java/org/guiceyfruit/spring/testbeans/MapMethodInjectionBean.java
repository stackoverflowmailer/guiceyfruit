/**
 *
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package org.guiceyfruit.spring.testbeans;

import org.guiceyfruit.spring.testbeans.TestBean;
import org.springframework.beans.factory.annotation.Autowired;
import java.util.Map;

/** @version $Revision: 1.1 $ */
public class MapMethodInjectionBean {

  private TestBean testBean;

  private Map<String, TestBean> testBeanMap;

  @Autowired(required = false)
  public void setTestBeanMap(TestBean testBean, Map<String, TestBean> testBeanMap) {
    this.testBean = testBean;
    this.testBeanMap = testBeanMap;
  }

  public TestBean getTestBean() {
    return this.testBean;
  }

  public Map<String, TestBean> getTestBeanMap() {
    return this.testBeanMap;
  }
}
