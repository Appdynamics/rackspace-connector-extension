package com.appdynamics.connectors;

import org.jclouds.cloudservers.CloudServersAsyncClient;
import org.jclouds.cloudservers.CloudServersClient;
import org.jclouds.compute.ComputeService;
import org.jclouds.compute.ComputeServiceContext;
import org.jclouds.rest.RestContext;

import com.singularity.ee.util.clock.ClockUtils;


public class RackSpaceCloudServerProvider {
	
	private final ComputeServiceContext compteServiceCtx;
	
	private final RestContext<CloudServersClient, CloudServersAsyncClient> provider;
	
	private final long creationTimestamp;
	
	public RackSpaceCloudServerProvider (ComputeServiceContext computeServiceCtx)
	{
		this.compteServiceCtx = computeServiceCtx;
		this.provider = this.compteServiceCtx.getProviderSpecificContext();
		this.creationTimestamp = ClockUtils.getCurrentTime();
	}
	
	public long getCreationTimestamp()
	{
		return this.creationTimestamp;
	}
	
	public CloudServersAsyncClient getAsyncApi()
	{
		return provider.getAsyncApi();
	}
	
	public CloudServersClient getSyncApi()
	{
		return provider.getApi();	
	}
	
	public ComputeService getComputeService()
	{
		return this.compteServiceCtx.getComputeService();
	}

	public void close()
	{
	    this.getComputeService().getContext().close();
	}
}
