package com.singularity.ee.connectors.rackspace;

import java.util.HashMap;
import java.util.Map;
import java.util.Properties;

import org.jclouds.Constants;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.compute.ComputeServiceContextFactory;
import org.jclouds.logging.slf4j.config.SLF4JLoggingModule;
import org.jclouds.ssh.jsch.config.JschSshClientModule;

import com.google.common.collect.ImmutableSet;
import com.google.inject.Module;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IProperty;
import com.singularity.ee.util.clock.ClockUtils;

class ConnectorLocator {
	
	private static final ConnectorLocator INSTANCE = new ConnectorLocator();
	
	private final Map<String, RackSpaceCloudServerProvider> userNameComputeServiceCtx = 
				new HashMap<String, RackSpaceCloudServerProvider>();
		
	private final Object connectorLock = new Object();
	
	private ConnectorLocator()
	{
        Runtime.getRuntime().addShutdownHook(new Thread() { 
            @Override
            public void run()
            {
                for (RackSpaceCloudServerProvider provider : userNameComputeServiceCtx.values())
                {
                    try
                    {
                        provider.close();
                    }
                    catch (Exception e)
                    {
                        // ignore
                    }
                }
            }
        });	
	}
	
	public static ConnectorLocator getInstance()
	{
		return INSTANCE;		
	}
	
	public RackSpaceCloudServerProvider getConnector(IComputeCenter computeCenter, IControllerServices controllerServices)
	{
		return getConnector(computeCenter.getProperties(),controllerServices,true);
	}
	
	public RackSpaceCloudServerProvider getConnector(IProperty[] properties, IControllerServices controllerServices, boolean validate)
	{
		String userName = Utils.getUserName(properties, controllerServices);
		String apiKey = Utils.getApiKey(properties, controllerServices);
		String location = Utils.getLocation(properties, controllerServices);
		return getConnector(userName, apiKey, location, validate);
	}
	
	public RackSpaceCloudServerProvider getConnector(String userName, String apiKey, String location, boolean validate)
	{
		synchronized (connectorLock)
		{
			String identifier = userName + apiKey + location;
			
			RackSpaceCloudServerProvider provider = userNameComputeServiceCtx.get(identifier);
			
			if (provider != null)
			{
	            long minAge = ClockUtils.getCurrentTime() - 23 * 60 * 60 * 1000;
			    
	            if (provider.getCreationTimestamp() < minAge || !validateProvider(validate, provider, false))
	            {
	            	userNameComputeServiceCtx.remove(identifier);
	                
	                try
                    {
                        provider.close();
                    }
                    catch (Exception e)
                    {
                        // ignore
                    }
	            }
	            else
	            {
	                return provider;
	            }
	            
	            return provider;
			}
			
			Properties overrides = new Properties();
			overrides.setProperty(Constants.PROPERTY_TRUST_ALL_CERTS, "true"); 
		    overrides.setProperty(Constants.PROPERTY_RELAX_HOSTNAME, "true");

			ComputeServiceContext computeServiceCtx = new ComputeServiceContextFactory()
				.createContext("cloudservers-" + location, userName, apiKey,
						ImmutableSet.<Module>of(new JschSshClientModule(),new SLF4JLoggingModule()), overrides);
			
			provider = new RackSpaceCloudServerProvider(computeServiceCtx);
			
			validateProvider(validate, provider, true);
			
			userNameComputeServiceCtx.put(identifier, provider);
			
			return provider;
		}
	}
	
    private boolean validateProvider(boolean validate, RackSpaceCloudServerProvider provider, boolean throwExceptionOnInvalid)
    {
        boolean valid = true;
        
        if (validate)
        {
            try
            {
                provider.getComputeService().listHardwareProfiles();
            }
            catch (RuntimeException e)
            {
                if (throwExceptionOnInvalid)
                {
                    throw e;
                }
                
                valid = false;
            }
        }
        
        return valid;
    }

}
