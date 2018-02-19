/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.connectors.rackspace;

import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IProperty;

class Utils
{
	private static final String USER_NAME = "User Name";
	
	private static final String API_KEY = "Api Key";
	
	public static final String IMAGE_ID = "Image ID";
	
	public static final String SHARED_IP_GROUP = "Shared IP Group Id";
    
	public static final String SHARED_IP = "Shared IP";
	
	public static final String SERVER_SIZE = "Server Size";
	
	public static final String LOCATION = "Location";
	
	private Utils() {}

	public static String getUserName(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, USER_NAME));
	}

	public static String getApiKey(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, API_KEY));
	}

	public static String getSharedIpGroup(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, SHARED_IP_GROUP));		
	}

	public static String getSharedIp(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, SHARED_IP));		
	}
	
	public static String getLocation(IProperty[] properties, IControllerServices controllerServices)
	{
		return getValue(controllerServices.getStringPropertyValueByName(properties, LOCATION));		
	}
	
	public static int getImageId(IProperty[] properties, IControllerServices controllerServices)
	{
		return Integer.parseInt(
				getValue(controllerServices.getStringPropertyValueByName(properties, IMAGE_ID)));		
	}	
	
    public static int getFlavorId(IProperty[] properties, IControllerServices controllerServices)
    {
		String size = (controllerServices.getStringPropertyValueByName(properties, SERVER_SIZE));
		
		if (size.equals("RAM: 256MB Disk: 10GB"))
		{
			return 1;
		}
		else if (size.equals("RAM: 512MB Disk: 20GB"))
		{
			return 2;
		}
		else if (size.equals("RAM: 1024MB Disk: 40GB"))
		{
			return 3;
		}
		else if (size.equals("RAM: 2048MB Disk: 80GB"))
		{
			return 4;
		}
		else if (size.equals("RAM: 4096MB Disk: 160GB"))
		{
			return 5;
		}
		else if (size.equals("RAM: 8192MB Disk: 320GB"))
		{
			return 6;
		}
		else if (size.equals("RAM: 15872MB Disk: 620GB"))
		{
			return 7;
		}
		else if (size.equals("RAM: 30720MB Disk: 1200GB"))
		{
			return 8;
		}
		else
		{
			return 1;
		}
    }
	
	private static String getValue(String value)
	{
		return (value == null || value.trim().length() == 0) ? null : value.trim();
	}

}
