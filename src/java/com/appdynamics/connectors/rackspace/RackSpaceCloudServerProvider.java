/**
 * Copyright 2013 AppDynamics, Inc.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.appdynamics.connectors.rackspace;

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
