/*******************************************************************************
 * Copyright(c) 2023 the original author Eduardo Iglesias Taylor.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * 	 https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 *
 * Contributors:
 * 	Eduardo Iglesias Taylor - initial API and implementation
 *******************************************************************************/
package org.platkmframework.doi;

import java.io.File;
import java.io.FileInputStream;
import java.lang.reflect.Constructor;
import java.lang.reflect.Field;
import java.lang.reflect.InvocationHandler;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.lang.reflect.Proxy;
import java.net.URISyntaxException;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.zip.ZipEntry;
import java.util.zip.ZipInputStream;

import javax.persistence.Entity;

import org.apache.commons.lang3.StringUtils;
import org.platkmframework.annotation.Api;
import org.platkmframework.annotation.ApplicationConfig;
import org.platkmframework.annotation.AutoWired;
import org.platkmframework.annotation.ClassMethod;
import org.platkmframework.annotation.Component;
import org.platkmframework.annotation.Controller;
import org.platkmframework.annotation.Factory;
import org.platkmframework.annotation.Functional;
import org.platkmframework.annotation.HttpRest;
import org.platkmframework.annotation.JBean;
import org.platkmframework.annotation.PropertyFileInfo;
import org.platkmframework.annotation.Repository;
import org.platkmframework.annotation.Service;
import org.platkmframework.annotation.TruslyException;
import org.platkmframework.annotation.db.DatabaseConfig;
import org.platkmframework.annotation.db.ESearchFilter;
import org.platkmframework.annotation.db.QSearchFilter;
import org.platkmframework.annotation.db.SearchFilters;
import org.platkmframework.annotation.db.SelectOption;
import org.platkmframework.annotation.db.SelectOptions;
import org.platkmframework.annotation.db.SystemColumnValue;
import org.platkmframework.annotation.limit.ApplicationLimit;
import org.platkmframework.annotation.rmi.RMIServer;
import org.platkmframework.annotation.security.SecurityCofing;
import org.platkmframework.annotation.timer.TimerFixeDelayScheduler;
import org.platkmframework.annotation.timer.TimerFixeRateScheduler;
import org.platkmframework.annotation.timer.TimerScheduler;
import org.platkmframework.doi.data.ObjectReferece;
import org.platkmframework.doi.exception.IoDCException;
import org.platkmframework.util.Util;
import org.platkmframework.util.error.InvocationException;
import org.platkmframework.util.proxy.ProxyProcessorFactory;
import org.platkmframework.util.reflection.ReflectionUtil; 


/**
 *   Author: 
 *     Eduardo Iglesias
 *   Contributors: 
 *   	Eduardo Iglesias - initial API and implementation
 *  Read sources to seeking classes with annotations
 *  Particular annotations
 *  -Controller:used to session scope
 *  -Path:used to application scope
 *  
 *  Controller: They are instanced in user login successed
 * @author Eduardo Iglesias Taylor
 *
 */
public class SearchClasses implements IoDProcess
{
	
	public static final String C_SERVICE_ANNNOTATION_ACTIVE = "service.annotation.active";

	public SearchClasses() { 
	}
 
	@Override
	public Map<Object, List<Method>> process(String packageNames,
												String[]  packagesPrefix,
													ObjectReferece objectReferece) throws IoDCException{ 
		
		Map<Object, List<Method>> methods = new HashMap<>();
		Map<String, Object> mInterface = new HashMap<>();
		
		Map<Field, List<Object>> mField = new HashMap<>();  
		List<Constructor<?>> listPendingClass = new ArrayList<>();
		try{		 	
 
			if(StringUtils.isNotEmpty(packageNames)){
				
				String[] arrPackages = packageNames.split(";"); //packageNames.split(",") solo si se buscan los paquetes por l informacion en web.xml
				if(arrPackages != null) {
					for (int i = 0; i < arrPackages.length; i++) {
						process(arrPackages[i], packagesPrefix, objectReferece, 
								mField, mInterface, listPendingClass, methods);
					}
				}
			}
		  
			//process pending classes
			_processPendingClasses(objectReferece, mField, mInterface, listPendingClass, true);
		     
			 //process autowired
			 _processAutowired(objectReferece, mField, mInterface); 
	        
		} catch (Exception e) 
		{ 
			e.printStackTrace();
		}	
		mField.clear();
		mField = null;
		 
		return methods;
	}

 
	/**
	 * 
	 * @param m
	 * @param mInterface
	 * @param mPendingClass
	 * @throws IoDCException 
	 * @throws InvocationException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 */
	private void _processPendingClasses(ObjectReferece objectReferece, 
										Map<Field, List<Object>> mField,
										Map<String, Object> mInterface,
										List<Constructor<?>> listPendingClass, boolean firstCheck) throws IoDCException, InvocationException, IllegalArgumentException, IllegalAccessException 
	{
		System.out.println("PENDING...");
		Class<?>[] parameters;
		List<Object> listParam = new ArrayList<>();
		List<Constructor<?>> listPendingClassSecondCheck  = new ArrayList<>();
		Object obj;
		for (Constructor<?> constructor : listPendingClass)
		{
			parameters = constructor.getParameterTypes();
			if(parameters != null)
			{
				listParam.clear();
				for (int i = 0; i < parameters.length; i++) 
				{
					obj = objectReferece.getObject(parameters[i].getName(), null);
					if (obj == null){ 
						obj = mInterface.get(parameters[i].getName());
					}	
					if (obj == null){ 	
						if(firstCheck) {
							listPendingClassSecondCheck.add(constructor);
						}else {
							throw new IoDCException("Configuration error, there  are not and object to set in constructor parameter " + constructor.getName() + "-" +parameters[i].getName());
						}
					}else{
						listParam.add(obj); 
					}
				}
				
				if(listParam.size() == parameters.length){
					obj = ReflectionUtil.createInstance(constructor, listParam.toArray());
					
					System.out.println("PENDING... " + obj.getClass().getName() );
					System.out.println(firstCheck);
					objectReferece.addObject(null, obj);
					
					generalProcess(obj,mField,objectReferece,mInterface);	 
					_processInfoFromObjectMethod(obj, mField, objectReferece, mInterface);
				}
			}  
		} 
		
		if(!listPendingClassSecondCheck.isEmpty()) {
			_processPendingClasses(objectReferece, mField, mInterface, listPendingClassSecondCheck, false);
		}
		
	}
 
 
	private void process(String packageName, 
						 String[]  packagesPrefix,
						 ObjectReferece objectReferece,
						 Map<Field, List<Object>> mField,
						 Map<String, Object> mInterface,
						 List<Constructor<?>> listPendingClass,
						 Map<Object, List<Method>> methods) throws IoDCException, InvocationException, URISyntaxException{
		
		File url = new File(packageName);
		if(url != null && url.exists())
			if(!url.getName().endsWith(".jar"))
			{
				try {
					_process(packageName, packagesPrefix, url, objectReferece, mField, mInterface, listPendingClass, methods);	
				}catch(Exception e) {
					throw new IoDCException(e.getMessage() + "- " + url.getName());
				} 
			}else {
				try {
					
					ZipInputStream zip = new ZipInputStream(new FileInputStream(packageName));
					String className;
					for (ZipEntry entry = zip.getNextEntry(); entry != null; entry = zip.getNextEntry()) {
					    if (!entry.isDirectory() && entry.getName().endsWith(".class")){ 
					    	className = entry.getName().substring(0, entry.getName().lastIndexOf(".")).replace('/', '.'); 
					    	if(containsPrefix(packagesPrefix, className)) {
						    	try {
						    		Class<?> class1 = Class.forName(className, true, Thread.currentThread().getContextClassLoader()); 
							        _classProcess(class1, objectReferece, mField, mInterface, listPendingClass, methods); 
						    	}catch (ClassNotFoundException e) {
									 System.out.println(className +  " - Exception NOT FOUND");
								} 
					    	}
					    }
					}
					zip.close(); 
				} catch (Exception e) { 
					e.printStackTrace();
				}
			}
	}
 
 
	/**
	 * 
	 * @param packageName
	 * @param folder
	 * @param m
	 * @param aux
	 * @throws URISyntaxException
	 * @throws ClassNotFoundException
	 * @throws InvocationException
	 * @throws IoDCException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 */
	protected  void _process(String packageName, 
							 String[] packagesPrefix,
							 File folder, 
							 ObjectReferece objectReferece, 
							 Map<Field, List<Object>> mField,
							 Map<String, Object> mInterface,
							 List<Constructor<?>> listPendingClass,
							 Map<Object, List<Method>> methods) throws InvocationException, IoDCException, IllegalArgumentException, IllegalAccessException{  
		try 
		{
			File[] files = folder.listFiles();
			if(files != null) 
				for (int i = 0; i < files.length; i++) 
				{
					File file = files[i];
					if(file.exists()) 
						if(file.isDirectory()) 
							_process(packageName + "." + file.getName(), packagesPrefix, file, objectReferece,
									 mField, mInterface, listPendingClass, methods);
						else if(file.getName().endsWith(".class"))
						{
							String classNamePackage = packageName + "." + file.getName().substring(0, file.getName().lastIndexOf("."));
							//esto es porque no se esta buscando la informacion en el web.xml,  sale el camino con paquete y nombre de clase
							classNamePackage = classNamePackage.substring(classNamePackage.indexOf(".") + 1);
							if( containsPrefix(packagesPrefix, classNamePackage) ) {
								Class<?> class1 = Class.forName(classNamePackage,true, Thread.currentThread().getContextClassLoader()); 
								_classProcess(class1, objectReferece, mField, mInterface, listPendingClass, methods); 
							} 
						} 
				}
			 
		} catch (ClassNotFoundException e) 
		{ 
			e.printStackTrace();
			throw new InvocationException("Error loading the project objetcts");
		}
	}

	private boolean containsPrefix(String[] packagesPrefix, String classNamePackage) {
		
		if( packagesPrefix != null && StringUtils.isNotBlank(classNamePackage)) { 
			for (int i = 0; i < packagesPrefix.length; i++) {
				if(classNamePackage.startsWith(packagesPrefix[i].trim()))
					return true;
			}
		} 
		return false;
	}

	private void _classProcess(Class<?> class1,
								ObjectReferece objectReferece, 
								Map<Field, List<Object>> mField,
								Map<String, Object> mInterface,
								List<Constructor<?>> listPendingClass,
								Map<Object, List<Method>> methods) throws InvocationException, IoDCException, IllegalArgumentException, IllegalAccessException {
		
		if(class1.isAnnotationPresent(Factory.class) ||  
		   class1.isAnnotationPresent(Component.class) ||
		   isServicesAndActiveChecked(class1, objectReferece) ||
		   class1.isAnnotationPresent(Api.class) || 
		   class1.isAnnotationPresent(Controller.class)|| 
		   class1.isAnnotationPresent(Repository.class)|| 
		   class1.isAnnotationPresent(RMIServer.class)|| 
		   class1.isAnnotationPresent(SecurityCofing.class)|| 
		   class1.isAnnotationPresent(DatabaseConfig.class)||
		   class1.isAnnotationPresent(ApplicationConfig.class)||
		   class1.isAnnotationPresent(HttpRest.class)
		   ){
			
			_processSearchOptionMapping(objectReferece, class1);
			
			if(!_hasCosntructorParam(class1,listPendingClass)){
				
				Object ob;
				if(class1.isAnnotationPresent(HttpRest.class)) {
					
					ob = Proxy.newProxyInstance(
							  this.getClass().getClassLoader(), 
							  new Class<?>[] { class1 }, 
							  new InvocationHandler() { 
								@Override
								public Object invoke(Object proxy, Method method, Object[] args) throws Throwable {
									return ProxyProcessorFactory.instance().seek(HttpRest.class.getName()).run(proxy, class1, method, args); 
								}
							  } 
						);
				}else {
					ob = ReflectionUtil.createInstance(class1);
				}
				
				if(class1.isAnnotationPresent(Api.class)) {
					processApiKey(objectReferece, ob);
				}else {
					objectReferece.addObject(null, ob); 
				}
				
				generalProcess(ob,mField,objectReferece,mInterface);	 
				_processInfoFromObjectMethod(ob, mField, objectReferece, mInterface);
			}
		}else{
			
			if(class1.isAnnotationPresent(Entity.class)) {
				objectReferece.getEntities().add(class1);
			}
			
			if(class1.isAnnotationPresent(ApplicationLimit.class)) { 
				Optional<Object> obLimit = objectReferece.getLimits().stream().filter(o -> o.getClass().getName().equals(class1.getName())).findFirst(); 
				if(!obLimit.isPresent()) {
					objectReferece.getLimits().add(ReflectionUtil.createInstance(class1));
				} 
			}
			
			if(class1.isAnnotationPresent(TruslyException.class)) {
				objectReferece.getExceptions().add(class1.getName());
			}
			
			if(class1.isAnnotationPresent(Functional.class)) {
				objectReferece.getFunctionals().add(ReflectionUtil.createInstance(class1));
			}
			
			
		} 
		
		/**if(class1.isAnnotationPresent(ApplicationConfig.class)){
			findMethodsToExecute(class1, methods);
		}*/
	}
	
	private void _processSearchOptionMapping(ObjectReferece objectReferece, Class<?> class1) {
		
		if(class1.isAnnotationPresent(SearchFilters.class)){
			SearchFilters searchFilters = class1.getAnnotation(SearchFilters.class);
			for (ESearchFilter eSearchFilter : searchFilters.entitySearchFilters()) {
				objectReferece.getSearchFilter().put(eSearchFilter.code(), eSearchFilter);
			}
			for (QSearchFilter qSearchFilter : searchFilters.querySearchFilters()) {
				objectReferece.getSearchFilter().put(qSearchFilter.code(), qSearchFilter);
			}
		}
		
		if(class1.isAnnotationPresent(ESearchFilter.class)) {
			ESearchFilter eSearchFilter = class1.getAnnotation(ESearchFilter.class);
			objectReferece.getSearchFilter().put(eSearchFilter.code(), eSearchFilter);
		
		}
		
		if(class1.isAnnotationPresent(QSearchFilter.class)) {
			QSearchFilter qSearchFilter = class1.getAnnotation(QSearchFilter.class);
			objectReferece.getSearchFilter().put(qSearchFilter.code(), qSearchFilter);
		}
		
		if(class1.isAnnotationPresent(SelectOptions.class)) {
			SelectOptions selectOptions = class1.getAnnotation(SelectOptions.class);
			for (SelectOption selectOption : selectOptions.selectOption()) {
				objectReferece.getSearchFilter().put(selectOption.code(), selectOptions);
			}
		}
		
		if(class1.isAnnotationPresent(SelectOptions.class)) {
			
			SelectOptions selectOptions = class1.getAnnotation(SelectOptions.class);
			for (SelectOption selectOption : selectOptions.selectOption()) {
				objectReferece.getSearchFilter().put(selectOption.code(), selectOption);
			}
		}
		
		if(class1.isAnnotationPresent(SelectOption.class)) {
			SelectOption selectOption = class1.getAnnotation(SelectOption.class);
			objectReferece.getSearchFilter().put(selectOption.code(), selectOption);
		}
		
	}

	private void _processInfoFromObjectMethod(Object ob, Map<Field, List<Object>> mField, ObjectReferece objectReferece, Map<String, Object> mInterface) throws InvocationException, IllegalArgumentException, IoDCException {
		try {
			Method[] methods = ob.getClass().getMethods();
			if(methods != null){
				Method method;
				Object beanObj;
				for (int i = 0; i < methods.length; i++){
					method = methods[i]; 
					if(method.isAnnotationPresent(JBean.class)){
						beanObj = method.invoke(ob);
						objectReferece.addObject(method.getAnnotation(JBean.class).name(),  beanObj);
						generalProcess(beanObj, mField, objectReferece, mInterface);
					}
				}
			}
		} catch (IllegalAccessException | InvocationTargetException e) {
			e.printStackTrace();
			throw new InvocationException("Error loading the project objetcts");
		}
	}

	private void generalProcess(Object ob, Map<Field, List<Object>> mField, ObjectReferece objectReferece, Map<String, Object> mInterface) throws IllegalArgumentException, IllegalAccessException, IoDCException, InvocationException {
		_checkAutoWired(ob, mField, objectReferece);
		_processPropertyInfo(ob, mField, objectReferece);
		_referencesByInterface(ob, mInterface);
		System.out.println(ob.getClass().getName());
	}

	private void processApiKey(ObjectReferece objectReferece, Object ob) {
		
		Method[] methods = ob.getClass().getMethods();
		if(methods != null && methods.length > 0) {
			Method method;
			ClassMethod classMethod; 
			Api api = ob.getClass().getAnnotation(Api.class);
			String key = api.path();
			
			Map<String, List<Method>> mapMethod = new HashMap<>();
			
			for (int i = 0; i < methods.length; i++) {
				method = methods[i];
				if(method.isAnnotationPresent(ClassMethod.class)){
					classMethod = method.getAnnotation(ClassMethod.class);
					
					if(!mapMethod.containsKey(classMethod.method().name())) {
						mapMethod.put(classMethod.method().name(), new ArrayList<>());
					}
					 
					mapMethod.get(classMethod.method().name()).add(method);
				}
			}
			objectReferece.addApiInfo(key, ob, mapMethod);  
		}
	}


/**	
 * @TODO AfterStartup
 * private void findMethodsToExecute(Class<?> class1, Map<Object, List<Method>> methods) throws InvocationException {
		Object ob = ReflectionUtil.createInstance(class1); 
		List<Method> list = new  ArrayList<>();
		for (Method method : ob.getClass().getMethods()) {
			if(method.isAnnotationPresent(AfterStartUp.class)){
				list.add(method);
			}
		}
		if(list.size() >0) {
			methods.put(ob, list);
		}
	}
*/

	private boolean isServicesAndActiveChecked(Class<?> class1, ObjectReferece objectReferece) {
		
		if(!class1.isAnnotationPresent(Service.class)) return false;
		
		Service service = class1.getAnnotation(Service.class);
		if(service.key().isEmpty() || service.value().isEmpty()) return true;
		
		Object value = objectReferece.getProp().get(service.key());
		
		return value !=null && service.value().equals(value.toString()); 
	}
  
	/**
	 * 
	 * @param class1
	 * @param mPendingClass
	 * @return
	 */
	private boolean _hasCosntructorParam(Class<?> class1, List<Constructor<?>> listPendingClass){
		Constructor<?>[] constructors = class1.getConstructors();
		if(constructors != null && constructors.length>0)
		{
			Constructor<?> constuctor = constructors[0];
			Class<?>[] param = constuctor.getParameterTypes();
			if(param != null && param.length > 0)
			{
				listPendingClass.add(constructors[0]);
				return true;
			}
		}
		return false;
	}

	
	/**
	 * 
	 * @param ob
	 * @param mInterface
	 */
	private void _referencesByInterface(Object ob, Map<String, Object> mInterface){
		Class<?>[] interfaces = ob.getClass().getInterfaces();
		if(interfaces!=null)
			for (int i = 0; i < interfaces.length; i++) 
				mInterface.put(interfaces[i].getName(), ob);  
	}

	/**
	 * 
	 * @param ob
	 * @param aux
	 * @throws IoDCException 
	 * @throws IllegalAccessException 
	 * @throws IllegalArgumentException 
	 */
	private void _checkAutoWired(Object ob, Map<Field, List<Object>> aux, ObjectReferece objectReferece) throws IoDCException, IllegalArgumentException, IllegalAccessException{
 
		List<Field> fields = ReflectionUtil.getAllFieldHeritage(ob.getClass());
		for (int i = 0; i < fields.size(); i++) 
		{
			Field f = fields.get(i);
			if(f.isAnnotationPresent(AutoWired.class)) 
			{
				if(!aux.containsKey(f)) aux.put(f, new ArrayList<>()); 
				aux.get(f).add(ob); 
			}else if(f.getAnnotations() != null && f.getAnnotations().length>0) {
				checkCustomIoD(ob, f);
			}
		} 
		
		List<Method> methods = ReflectionUtil.getAllMethoddHeritage(ob.getClass());
		for (int i = 0; i < methods.size(); i++) 
		{
			Method m = methods.get(i);
			if(m.isAnnotationPresent(TimerFixeDelayScheduler.class) || 
					m.isAnnotationPresent(TimerFixeRateScheduler.class) || 
					m.isAnnotationPresent(TimerScheduler.class) || 
					m.isAnnotationPresent(SystemColumnValue.class)) 
			{
				objectReferece.addBeanMethod(ob, m);  
			} 
		} 
	}	
 

	protected void checkCustomIoD(Object ob, Field f) throws IoDCException{ 
		
	}


	private void _processPropertyInfo(Object obj, Map<Field, List<Object>> mField, ObjectReferece objectReferece) throws InvocationException {
		
		if(objectReferece.getProp() !=null){
			List<Field> fields = ReflectionUtil.getAllFieldHeritage(obj.getClass());
			PropertyFileInfo propertyFileInfo; 
			boolean accessValue;
			Field f;
			for (int i = 0; i < fields.size(); i++){
				f = fields.get(i);
				if(f.isAnnotationPresent(PropertyFileInfo.class)){
					propertyFileInfo = f.getAnnotation(PropertyFileInfo.class);
					accessValue = f.canAccess(obj);
					f.setAccessible(true); 
				    try {
						f.set(obj, getPropertyFileInfoValue(f, propertyFileInfo.name(), objectReferece));
					} catch (IllegalArgumentException | IllegalAccessException e) {
						e.printStackTrace(); 
						throw new InvocationException("setting property error-> " + e.getMessage());
					} 
				    f.setAccessible(accessValue); 
				}	 
			} 
		}
		
	}
	
	
	private Object getPropertyFileInfoValue(Field field, String propertyKey, ObjectReferece objectReferece) throws InvocationException {
		 
		if(propertyKey.indexOf("$")>=0) return Util.checkingArgs(propertyKey, objectReferece.getProp());
			
		String value = objectReferece.getProp().getProperty(propertyKey);
		if(StringUtils.isBlank(value)) return ""; 
		 
		return  ReflectionUtil.getRealAttributeValue(field, Util.checkingArgs(value, objectReferece.getProp()));
		
	}


	/**
	 * 
	 * @param aux: Field to set information autowired
	 * @throws IoDCException 
	 * @throws InvocationException 
	 */
	private void _processAutowired(ObjectReferece objectReferece, 
								   Map<Field, List<Object>> mField,
								   Map<String, Object> mInterface) throws IoDCException, InvocationException 
	{
		try 
		{
			Field f   = null;
			List<Object> objList = null; 
			String obClassName;
			boolean accessValue;
			for (Map.Entry<Field, List<Object>> entry : mField.entrySet())
			{
				f = entry.getKey();
				objList = entry.getValue();
				
				obClassName = f.getType().getName();
				Object obAutowire  = objectReferece.getObject(obClassName, f.getAnnotation(AutoWired.class).key());
				if(obAutowire == null)
					obAutowire = mInterface.get(obClassName);
				if(obAutowire == null)
					   throw new IoDCException("Configuration error, there are not and object to set in field " + f.getDeclaringClass() + " " + obClassName + " " +f.getName() );
  	 
				for (int i = 0; i < objList.size(); i++)
				{
					accessValue = f.canAccess(objList.get(i));
					f.setAccessible(true);
					f.set(objList.get(i), obAutowire); 
					f.setAccessible(accessValue);
				}
			}	
			
		} catch (IllegalArgumentException | IllegalAccessException e) 
		{
			e.printStackTrace();
			throw new InvocationException("autowired error-> " + e.getMessage());
		}
	}
 
	
}
