/*
 *   Copyright 2018. AppDynamics LLC and its affiliates.
 *   All Rights Reserved.
 *   This is unpublished proprietary source code of AppDynamics LLC and its affiliates.
 *   The copyright notice above does not evidence any actual or intended publication of such source code.
 *
 */
package com.appdynamics.connectors.rackspace;

import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.CONTROLLER_SERVICES_PORT_PROPERTY_KEY;
import static com.singularity.ee.controller.KAppServerConstants.DEFAULT_CONTROLLER_PORT_VALUE;

import java.net.InetAddress;
import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.logging.Level;
import java.util.logging.Logger;

import org.jclouds.cloudservers.CloudServersClient;
import org.jclouds.cloudservers.domain.Addresses;
import org.jclouds.cloudservers.domain.RebootType;
import org.jclouds.cloudservers.domain.Server;
import org.jclouds.cloudservers.domain.ServerStatus;
import org.jclouds.cloudservers.domain.SharedIpGroup;
import org.jclouds.cloudservers.options.CreateServerOptions;
import org.jclouds.compute.domain.NodeMetadata;

import com.singularity.ee.agent.resolver.AgentResolutionEncoder;
import com.singularity.ee.connectors.api.ConnectorException;
import com.singularity.ee.connectors.api.IConnector;
import com.singularity.ee.connectors.api.IControllerServices;
import com.singularity.ee.connectors.api.InvalidObjectException;
import com.singularity.ee.connectors.entity.api.IAccount;
import com.singularity.ee.connectors.entity.api.IComputeCenter;
import com.singularity.ee.connectors.entity.api.IImage;
import com.singularity.ee.connectors.entity.api.IImageStore;
import com.singularity.ee.connectors.entity.api.IMachine;
import com.singularity.ee.connectors.entity.api.IMachineDescriptor;
import com.singularity.ee.connectors.entity.api.IProperty;
import com.singularity.ee.connectors.entity.api.MachineState;
import com.singularity.ee.controller.KAppServerConstants;

public class RackSpaceCloudServerConnector implements IConnector
{

	private IControllerServices controllerServices;

	private static final Object counterLock = new Object();

	private static volatile long counter;

	private final Logger logger = Logger.getLogger(RackSpaceCloudServerConnector.class.getName());

	@Override
	public IMachine createMachine(IComputeCenter computeCenter, IImage image,
			IMachineDescriptor machineDescriptor) throws InvalidObjectException, ConnectorException
	{
		boolean succeeded = false;
		Exception createFailureRootCause = null;
		Server server = null;

		IProperty[] macProps = machineDescriptor.getProperties();

		RackSpaceCloudServerProvider connector = ConnectorLocator.getInstance().getConnector(computeCenter,
				controllerServices);

		try
		{
			String controllerHost = System.getProperty(CONTROLLER_SERVICES_HOST_NAME_PROPERTY_KEY,
					InetAddress.getLocalHost().getHostAddress());

			int controllerPort = Integer.getInteger(CONTROLLER_SERVICES_PORT_PROPERTY_KEY,
					DEFAULT_CONTROLLER_PORT_VALUE);

			IAccount account = computeCenter.getAccount();

			String accountName = account.getName();
			String accountAccessKey = account.getAccessKey();
			AgentResolutionEncoder agentResolutionEncoder = null;

			try
			{
				agentResolutionEncoder = new AgentResolutionEncoder(controllerHost, controllerPort,
						accountName, accountAccessKey);
			}
			catch (Exception e)
			{
				throw new ConnectorException("Failed to initiate AgentResolutionEncoder");
			}

			server = createServer(agentResolutionEncoder, image, macProps, connector);

			IMachine machine = controllerServices.createMachineInstance(Integer.toString(server.getId()),
					agentResolutionEncoder.getUniqueHostIdentifier(), computeCenter, machineDescriptor,
					image, getAgentPort());

			logger.info("RackSpace Cloud Server machine created; machine id:" + machine.getId()
					+ "; server id:" + server.getId() + "; server name:" + server.getName());

			succeeded = true;
			return machine;
		}
		catch (Exception e)
		{
			createFailureRootCause = e;
			//
			throw new ConnectorException(e.getMessage(), e);
		}
		finally
		{

			if (!succeeded && server != null)
			{
				try
				{
					connector.getAsyncApi().deleteServer(server.getId());
				}
				catch (Exception e)
				{
					throw new ConnectorException("Machine create failed, but terminate failed as well! "
							+ "We have an orphan Rackspace Cloud Server instance with id: " + server.getId()
							+ " that must be shut down manually. Root cause for machine "
							+ "create failure is following: ", createFailureRootCause);
				}
			}
		}
	}

	// Assuming the server has to be injected with the agent info..
	private Server createServer(AgentResolutionEncoder agentResolutionEncoder, IImage image,
			IProperty[] macProps, RackSpaceCloudServerProvider connector) throws ConnectorException
	{
		int imageId = Utils.getImageId(image.getProperties(), controllerServices);

		int flavorId = Utils.getFlavorId(macProps, controllerServices);

		String shareIpGroup = Utils.getSharedIpGroup(macProps, controllerServices);

		String sharedIp = Utils.getSharedIp(macProps, controllerServices);

		String userData = agentResolutionEncoder.encodeAgentResolutionInfo();

		String filePath = KAppServerConstants.LINUX_FULL_PATH_TO_USER_DATA_FILE;

		if (image.getOsType().equals(IImage.WINDOWS_OS_TYPE))
		{
			filePath = KAppServerConstants.WINDOWS_FULL_PATH_TO_USER_DATA_FILE;
		}

		logger.info("Starting Rackspace Cloud Server machine from Image :" + imageId + "; flavor :"
				+ flavorId + shareIpGroup == null ? ""
				: ("; sharedIpGroup :" + shareIpGroup) + sharedIp == null ? "" : ("; sharedIp :" + sharedIp)
						+ "; userData :" + userData);

		CloudServersClient client = connector.getSyncApi();

		long count;

		synchronized (counterLock)
		{
			count = counter++;
		}

		CreateServerOptions options = CreateServerOptions.Builder.withFile(filePath, userData.getBytes());

		if (shareIpGroup != null)
		{
			options.withSharedIpGroup(getSharedIpGroup(shareIpGroup, connector));
		}

		if (sharedIp != null)
		{
			options.withSharedIp(getSharedIp(sharedIp, shareIpGroup, connector));
		}

		Server server = client.createServer("AD_" + System.currentTimeMillis() + count, imageId, flavorId,
				options);

		if (shareIpGroup != null)
		{
			controllerServices.getStringPropertyByName(macProps, Utils.SHARED_IP_GROUP)
					.setValue(shareIpGroup);
		}

		if (sharedIp != null)
		{
			controllerServices.getStringPropertyByName(macProps, Utils.SHARED_IP).setValue(sharedIp);
		}

		logger.info("Rackspace Cloud Server created. Id: " + server.getId() + "; Name:" + server.getName()
				+ "; default password:" + server.getAdminPass());

		return server;

	}

	public int getSharedIpGroup(String sharedGroupId, RackSpaceCloudServerProvider connector)
			throws ConnectorException
	{

		int groupId = Integer.parseInt(sharedGroupId);

		SharedIpGroup sharedIpGroup = connector.getSyncApi().getSharedIpGroup(groupId);

		if (sharedIpGroup == null)
		{
			throw new ConnectorException("Invalid Rackspace Cloud Server Shared Ip Group: " + sharedGroupId);
		}

		return groupId;
	}

	public String getSharedIp(String sharedIp, String sharedGroupId, RackSpaceCloudServerProvider connector)
			throws ConnectorException
	{
		if (sharedGroupId == null)
		{
			throw new ConnectorException("A server must be launched into a "
					+ "Shared Ip Group for it to be associated with a Shared IP");
		}

		int groupId = getSharedIpGroup(sharedGroupId, connector);

		SharedIpGroup sharedIpGroup = connector.getSyncApi().getSharedIpGroup(groupId);

		List<Integer> servers = sharedIpGroup.getServers();

		for (Integer id : servers)
		{
			Addresses serverAddresses = connector.getSyncApi().getAddresses(id);

			Set<String> publicAddresses = serverAddresses.getPublicAddresses();

			if (publicAddresses.contains(sharedIp))
			{
				return sharedIp;
			}
		}

		throw new ConnectorException("Invalid Rackspace Cloud Server Shared Ip: " + sharedIp);
	}

	// try to grab the ip address that is not shared
	public String getIpAddress(Server server, String sharedGroupId, String sharedIp,
			RackSpaceCloudServerProvider connector) throws ConnectorException
	{
		// check if server id is appropriate
		NodeMetadata nodeMetaData = connector.getComputeService().getNodeMetadata(
				Integer.toString(server.getId()));

		if (nodeMetaData == null)
		{
			throw new IllegalArgumentException("Invalid Rackspace Cloudserver id " + server.getId()
					+ " is specified");
		}

		Set<String> serverAddresses = connector.getSyncApi().getAddresses(server.getId())
				.getPublicAddresses();

		// check and return the ipaddress that is not shared.
		if (sharedIp != null && serverAddresses.toArray().length != 1)
		{
			// catch and silence errors in case the sharedIpGroup stored in the
			// controller database is not valid anymore.... for now
			try
			{
				int groupId = getSharedIpGroup(sharedGroupId, connector);

				SharedIpGroup sharedIpGroup = connector.getSyncApi().getSharedIpGroup(groupId);

				List<Integer> servers = sharedIpGroup.getServers();

				List<String> allAddresses = null;

				for (Integer id : servers)
				{
					if (id != server.getId())
					{
						Set<String> addresses = connector.getSyncApi().getAddresses(id).getPublicAddresses();

						if (allAddresses == null)
						{
							allAddresses = new ArrayList<String>(addresses);
						}
						else
						{
							allAddresses.addAll(new ArrayList<String>(addresses));
						}
					}
				}

				if (allAddresses != null)
				{
					for (String ip : serverAddresses)
					{
						if (!allAddresses.contains(ip))
						{
							return ip;
						}
					}
				}
			}
			catch (Exception e)
			{
				logger.info("Unable to obtain multiple Addresses for Rackspace Cloud Server Id: "
						+ server.getId() + " Message:" + e.getMessage());
			}
		}

		return serverAddresses.iterator().next();
	}

	@Override
	public void refreshMachineState(IMachine machine) throws InvalidObjectException, ConnectorException
	{
		MachineState currentState = machine.getState();
		IProperty[] macProps = machine.getMachineDescriptor().getProperties();

		String sharedIp = Utils.getSharedIp(macProps, controllerServices);
		String shareIpGroup = Utils.getSharedIpGroup(macProps, controllerServices);

		machine.getImage().getOsType();

		if (currentState == MachineState.STARTING)
		{
			RackSpaceCloudServerProvider connector = ConnectorLocator.getInstance().getConnector(
					machine.getComputeCenter(), controllerServices);

			Server server = connector.getSyncApi().getServer(Integer.parseInt(machine.getName()));

			if (server == null)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (server.getStatus() == ServerStatus.SUSPENDED)
			{
				connector.getAsyncApi().rebootServer(server.getId(), RebootType.SOFT);
			}
			else if (server.getStatus() == ServerStatus.ACTIVE)
			{
				String ipAddress = getIpAddress(server, sharedIp, shareIpGroup, connector);

				String currentIpAddress = machine.getIpAddress();

				if (ipAddress != null && !ipAddress.equals(currentIpAddress))
				{
					machine.setIpAddress(ipAddress);
				}

				machine.setState(MachineState.STARTED);
			}
		}
		else if (currentState == MachineState.STOPPING)
		{
			RackSpaceCloudServerProvider connector = ConnectorLocator.getInstance().getConnector(
					machine.getComputeCenter(), controllerServices);

			Server server = null;

			try
			{
				server = connector.getSyncApi().getServer(Integer.parseInt(machine.getName()));
			}
			catch (Exception e)
			{
				machine.setState(MachineState.STOPPED);
				logger.log(Level.FINE, "Exception occurred while checking machine "
						+ "state on STOPPING instance. Assume instance is STOPPED.", e);
			}

			if (server == null)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (server.getStatus() == ServerStatus.UNKNOWN
					|| server.getStatus() == ServerStatus.SUSPENDED)
			{
				machine.setState(MachineState.STOPPED);
			}
		}
	}

	@Override
	public void restartMachine(IMachine machine) throws InvalidObjectException, ConnectorException
	{
		RackSpaceCloudServerProvider connector = ConnectorLocator.getInstance().getConnector(
				machine.getComputeCenter(), controllerServices);

		connector.getAsyncApi().rebootServer(Integer.parseInt(machine.getName()), RebootType.HARD);
	}

	@Override
	public void terminateMachine(IMachine machine) throws InvalidObjectException, ConnectorException
	{
		try
		{
			RackSpaceCloudServerProvider connector = ConnectorLocator.getInstance().getConnector(
					machine.getComputeCenter(), controllerServices);

			Server server = connector.getSyncApi().getServer(Integer.parseInt(machine.getName()));

			if (server == null)
			{
				machine.setState(MachineState.STOPPED);
			}
			else if (server.getStatus() == ServerStatus.ACTIVE)
			{
				connector.getAsyncApi().deleteServer(server.getId());
			}
		}
		catch (Exception e)
		{
			throw new ConnectorException("Rackspace Cloud Server Machine terminate failed: "
					+ machine.getName(), e);
		}
	}

	@Override
	public void validate(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
		try
		{
			ConnectorLocator.getInstance().getConnector(computeCenter.getProperties(), controllerServices,
					true);
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "", e);

			throw new InvalidObjectException(
					"Failed to validate the Rackspace Cloud Server connector properties", e);
		}
	}

	@Override
	public void setControllerServices(IControllerServices controllerServices)
	{
		this.controllerServices = controllerServices;
	}

	@Override
	public void validate(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		try
		{
			ConnectorLocator.getInstance().getConnector(imageStore.getProperties(), controllerServices, true);
		}
		catch (Exception e)
		{
			logger.log(Level.WARNING, "", e);

			throw new InvalidObjectException(
					"Failed to validate the Rackspace Clouds Server image store properties", e);
		}
	}

	@Override
	public void validate(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void unconfigure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void unconfigure(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void unconfigure(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void configure(IComputeCenter computeCenter) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void configure(IImageStore imageStore) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void configure(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public void deleteImage(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

	@Override
	public int getAgentPort()
	{
		return controllerServices.getDefaultAgentPort();
	}

	@Override
	public void refreshImageState(IImage image) throws InvalidObjectException, ConnectorException
	{
		// do nothing
	}

}
