package org.dasein.cloud.google.platform;

//import static org.junit.Assert.assertNotNull;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

import javax.annotation.Nonnull;

import org.dasein.cloud.CloudErrorType;
import org.dasein.cloud.CloudException;
import org.dasein.cloud.InternalException;
import org.dasein.cloud.ProviderContext;
import org.dasein.cloud.ResourceStatus;
import org.dasein.cloud.TimeWindow;
import org.dasein.cloud.google.Google;
import org.dasein.cloud.google.GoogleException;
import org.dasein.cloud.google.capabilities.GCERelationalDatabaseCapabilities;
import org.dasein.cloud.identity.ServiceAction;
import org.dasein.cloud.platform.ConfigurationParameter;
import org.dasein.cloud.platform.Database;
import org.dasein.cloud.platform.DatabaseBackup;
import org.dasein.cloud.platform.DatabaseBackupState;
import org.dasein.cloud.platform.DatabaseConfiguration;
import org.dasein.cloud.platform.DatabaseEngine;
import org.dasein.cloud.platform.DatabaseProduct;
import org.dasein.cloud.platform.DatabaseSnapshot;
import org.dasein.cloud.platform.DatabaseState;
import org.dasein.cloud.platform.RelationalDatabaseCapabilities;
import org.dasein.cloud.platform.RelationalDatabaseSupport;
import org.dasein.cloud.util.APITrace;

import com.google.api.client.googleapis.json.GoogleJsonResponseException;
import com.google.api.services.sqladmin.SQLAdmin;
import com.google.api.services.sqladmin.model.BackupConfiguration;
import com.google.api.services.sqladmin.model.BackupRun;
import com.google.api.services.sqladmin.model.BackupRunsListResponse;
import com.google.api.services.sqladmin.model.DatabaseFlags;
import com.google.api.services.sqladmin.model.DatabaseInstance;
import com.google.api.services.sqladmin.model.Flag;
import com.google.api.services.sqladmin.model.FlagsListResponse;
import com.google.api.services.sqladmin.model.InstancesRestartResponse;
import com.google.api.services.sqladmin.model.IpConfiguration;
import com.google.api.services.sqladmin.model.IpMapping;
import com.google.api.services.sqladmin.model.LocationPreference;
import com.google.api.services.sqladmin.model.OperationError;
import com.google.api.services.sqladmin.model.Settings;
import com.google.api.services.sqladmin.model.Tier;
import com.google.api.services.sqladmin.model.TiersListResponse;

/*
 * https://developers.google.com/cloud-sql/faq#data_location
 */

public class RDS implements RelationalDatabaseSupport {
    static private Long gigabyte = 1073741824L;
    static private Long megabyte = 1048576L;
    static private volatile ArrayList<DatabaseEngine> engines = null;
    private volatile ArrayList<DatabaseProduct> databaseProducts = null;
    private Google provider;

	public RDS(Google provider) {
        this.provider = provider;
	}

	@Override
	public String[] mapServiceAction(ServiceAction action) {
	    // TODO: implement me
	    return new String[0];
	}

	@Override
	public void addAccess(String providerDatabaseId, String sourceCidr) throws CloudException, InternalException {
	    
	    // TODO test this.
	    ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        DatabaseInstance databaseInstance = null;
        try {
            databaseInstance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
        }

        IpConfiguration ipConfiguration = new IpConfiguration();
        List<String> authorizedNetworks = new ArrayList<String>();
        authorizedNetworks.add(sourceCidr);
        ipConfiguration.setAuthorizedNetworks(authorizedNetworks );
        Settings settings = databaseInstance.getSettings();
        settings.setIpConfiguration(ipConfiguration );
        databaseInstance.setSettings(settings);

        try {
            sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, databaseInstance);
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
        }
	}

	@Override
	public void alterDatabase(String providerDatabaseId, boolean applyImmediately, String productSize, int storageInGigabytes, String configurationId, String newAdminUser, String newAdminPassword, int newPort, int snapshotRetentionInDays, TimeWindow preferredMaintenanceWindow, TimeWindow preferredBackupWindow) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<Database> list = new ArrayList<Database>();
        DatabaseInstance databaseInstance = null;
        try {
            databaseInstance = sqlAdmin.instances().get(ctx.getAccountNumber(), providerDatabaseId).execute();
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
        }


        // productSize

        if (null != databaseInstance) {
            databaseInstance.setMaxDiskSize(storageInGigabytes * gigabyte);
        }

        
        // configurationId

        // newAdminUser

        // newAdminPassword

        // newPort

        // snapshotRetentionInDays

        // preferredMaintenanceWindow

        // preferredBackupWindow

        Settings settings = new Settings();
        //settings.setBackupConfiguration(backupConfiguration);
        //settings.setDatabaseFlags(databaseFlags);
        //settings.setIpConfiguration(ipConfiguration);
        //settings.setTier(tier);

        databaseInstance.setSettings(settings );
        //databaseInstance.setIpAddresses(ipAddresses)


        try {
            sqlAdmin.instances().update(ctx.getAccountNumber(), providerDatabaseId, databaseInstance);
    	} catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
        }

        // TODO add in blocking if applyImmediately is true
	}

	@Override
	public String createFromScratch(String dataSourceName, DatabaseProduct product, String databaseVersion, String withAdminUser, String withAdminPassword, int hostPort) throws CloudException, InternalException {
		APITrace.begin(provider, "RDBMS.createFromScratch");
		ProviderContext ctx = provider.getContext();
		SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
		try {
			DatabaseInstance content = new DatabaseInstance();
            String newDatabaseVersion = getDefaultVersion(product.getEngine()).replaceAll("\\.", "_");

			content.setInstance(dataSourceName);
			content.setDatabaseVersion(product.getEngine().name() + "_" + newDatabaseVersion);
			//content.setKind("sql#instance"); // REDUNDANT?
			content.setProject(ctx.getAccountNumber());
			content.setRegion(ctx.getRegionId().replaceFirst("[0-9]$", ""));  // Oddly setRegion needs just the base, no number after the region...
			// THINGS WE HAVE AND HAVE NOT USED
			// withAdminUser
			// withAdminPassword  // SQLAdmin.Instances.SetRootPassword 
			// hostPort
			// THINGS IT HAS AND DONT KNOW
			//content.setCurrentDiskSize(currentDiskSize);				// long
			//content.setMaxDiskSize(maxDiskSize);						// long
			//java.util.List<IpMapping> ipAddresses = new ArrayList<IpMapping>();
			//ipAddresses.add(new IpMapping().setIpAddress(ipAddress));	// String
			//content.setIpAddresses(ipAddresses);
			//SslCert serverCaCert = null;
			//content.setServerCaCert(serverCaCert );

			Settings settings = new Settings();
				settings.setActivationPolicy("ALWAYS");  // ALWAYS NEVER ON_DEMAND

				//java.util.List<BackupConfiguration> backupConfiguration;
				//BackupConfiguration element;
				//element.set(fieldName, value);
				//element.setBinaryLogEnabled(binaryLogEnabled);
				//element.setEnabled(enabled);
				//element.setId(id);
				//element.setStartTime(startTime);
				//backupConfiguration.set(0, element);
				//settings.setBackupConfiguration(backupConfiguration);

				//java.util.List<DatabaseFlags> databaseFlags;

				//DatabaseFlags element;
				//element.setName("name").setValue("value");
				// The name of the flag. These flags are passed at instance startup, so include both MySQL server options and MySQL system variables. Flags should be specified with underscores, not hyphens. Refer to the official MySQL documentation on server options and system variables for descriptions of what these flags do. Acceptable values are: event_scheduler on or off (Note: The event scheduler will only work reliably if the instance activationPolicy is set to ALWAYS.) general_log on or off group_concat_max_len 4..17179869184 innodb_flush_log_at_trx_commit 0..2 innodb_lock_wait_timeout 1..1073741824 log_bin_trust_function_creators on or off log_output Can be either TABLE or NONE, FILE is not supported. log_queries_not_using_indexes on or off long_query_time 0..30000000 lower_case_table_names 0..2 max_allowed_packet 16384..1073741824 read_only on or off skip_show_database on or off slow_query_log on or off wait_timeout 1..31536000
				//databaseFlags.set(0, element);
				//settings.setDatabaseFlags(databaseFlags);

				//IpConfiguration ipConfiguration;
				//ipConfiguration.setAuthorizedNetworks(authorizedNetworks);
				//ipConfiguration.setRequireSsl(requireSsl);
				//settings.setIpConfiguration(ipConfiguration);

				// settings.setKind("sql#settings"); // REDUNDANT?

				//LocationPreference locationPreference;
				//locationPreference.setZone(zone); //us-centra1-a, us-central1-b
				//settings.setLocationPreference(locationPreference);

				settings.setPricingPlan("PER_USE"); // This can be either PER_USE or PACKAGE
				settings.setReplicationType("SYNCHRONOUS");  // This can be either ASYNCHRONOUS or SYNCHRONOUS
				settings.setTier("D0"); // D0 D1 D2 D4 D8 D16 D32

			content.setSettings(settings);

			sqlAdmin.instances().insert(ctx.getAccountNumber(), content).execute();

			return dataSourceName;
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
		}
        finally {
            APITrace.end();
        }
	}

	@Override
	public String createFromLatest(String dataSourceName, String providerDatabaseId, String productSize, String providerDataCenterId, int hostPort) throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public String createFromSnapshot(String dataSourceName, String providerDatabaseId, String providerDbSnapshotId, String productSize, String providerDataCenterId, int hostPort) throws CloudException, InternalException {
		return null;
	}

	@Override
	public String createFromTimestamp(String dataSourceName, String providerDatabaseId, long beforeTimestamp, String productSize, String providerDataCenterId, int hostPort) throws InternalException, CloudException {
		// TODO Auto-generated method stub
		return null;
	}

    public Database getDatabase(String providerDatabaseId) throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getDatabase");
        try {
            if( providerDatabaseId == null ) {
                return null;
            }
            Iterable<Database> dbs = listDatabases();
            if (dbs != null)
	            for( Database database : dbs) 
	            	if (database != null)
		                if( database.getProviderDatabaseId().equals(providerDatabaseId) ) 
		                    return database;

            return null;
        } catch (Exception e) {
        	throw new CloudException(e);
        }

        finally {
            APITrace.end();
        }
    }

	@Override
	public Iterable<DatabaseEngine> getDatabaseEngines() throws CloudException, InternalException {
        APITrace.begin(provider, "RDBMS.getSupportedVersions");
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        HashMap<DatabaseEngine, Boolean> engines = new HashMap<DatabaseEngine, Boolean>();
        try {
            FlagsListResponse flags = sqlAdmin.flags().list().execute();
            for (Flag  flag : flags.getItems()) {
                List<String> appliesTo = flag.getAppliesTo();
                for (String dbNameVersion : appliesTo) {
                    String dbBaseName = dbNameVersion.replaceFirst("_.*", "");
                    engines.put(DatabaseEngine.valueOf(dbBaseName), true);
                }
            }
        }
        catch( IOException e ) {
            throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return engines.keySet();
	}

	@Override
    public String getDefaultVersion(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
	    if (forEngine == null)
	        return null;
        APITrace.begin(provider, "RDBMS.getDefaultVersion");
        try {
            Iterable<String> versions = getSupportedVersions(forEngine);
            for (String version : versions)
                return version;  // just return first...
        }
        finally {
            APITrace.end();
        }
        return null;
    }

	@Override
	public @Nonnull Iterable<String> getSupportedVersions(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
	    APITrace.begin(provider, "RDBMS.getSupportedVersions");
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();
        HashMap<String, Boolean> versions = new HashMap<String, Boolean>();
        try {
            FlagsListResponse flags = sqlAdmin.flags().list().execute();
            for (Flag  flag : flags.getItems()) {
                List<String> appliesTo = flag.getAppliesTo();
                for (String dbNameVersion : appliesTo) 
                    versions.put(dbNameVersion.toLowerCase().replaceFirst(forEngine.toString().toLowerCase() + "_", "").replaceAll("_", "."), true);
            }
        }
        catch( IOException e ) {
            throw new CloudException(e);
        }
        finally {
            APITrace.end();
        }
        return versions.keySet();
	}

	@Override
	public @Nonnull Iterable<DatabaseProduct> listDatabaseProducts(@Nonnull DatabaseEngine forEngine) throws CloudException, InternalException {
	    APITrace.begin(provider, "RDBMS.listDatabaseProducts");
	    ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<DatabaseProduct> products = new ArrayList<DatabaseProduct>();

        // TODO move this to JSON once it stabilizes.
        Map<String, Float> hourly = new HashMap<String, Float>();
        Map<String, Float> daily = new HashMap<String, Float>();
        hourly.put("D0", 0.025F);
        daily.put("D0", 0.36F);
        hourly.put("D1", 0.10F);
        daily.put("D1", 1.46F);
        hourly.put("D2", 0.19F);
        daily.put("D2", 2.93F);
        hourly.put("D4", 0.38F);
        daily.put("D4", 5.86F);
        hourly.put("D8", 0.77F);
        daily.put("D8", 11.71F);
        hourly.put("D16", 1.54F);
        daily.put("D16", 23.42F);
        hourly.put("D32", 3.08F);
        daily.put("D32", 46.84F);
        Map<String, Float> hourlyRate = Collections.unmodifiableMap(hourly);
        Map<String, Float> dailyRate = Collections.unmodifiableMap(hourly);


        try {
            TiersListResponse tierList = sqlAdmin.tiers().list(ctx.getAccountNumber()).execute();
            List<Tier> tiers = tierList.getItems();

            for (Tier t : tiers) {
                // Hourly rate
                DatabaseProduct product = null;
                int ramInMB = (int) ( t.getRAM() /  megabyte );
                product = new DatabaseProduct(t.getTier(), t.getTier() + " - " + ramInMB + "MB RAM Hourly");
                product.setEngine(forEngine);
                int sizeInGB = (int) ( t.getDiskQuota() / gigabyte );
                product.setStorageInGigabytes(sizeInGB);
                product.setCurrency("USD");
                product.setStandardHourlyRate(hourlyRate.get(t.getTier()));
                product.setStandardIoRate(0f);        // not charged for i think
                product.setStandardStorageRate(0f);   // not charged for i think
                product.setHighAvailability(false);       // unknown as yet
                for (String region : t.getRegion()) { // list of regions
                    product.setProviderDataCenterId(region); // Needs core change for product.setRegionId(region) 
                    products.add(product);
                }
                // Daily rate
                product = new DatabaseProduct(t.getTier(), t.getTier() + " - " + ramInMB + "MB RAM Daily");
                product.setEngine(forEngine);
                product.setStorageInGigabytes(sizeInGB);
                product.setCurrency("USD");
                product.setStandardHourlyRate(dailyRate.get(t.getTier()) / 24.0f);
                product.setStandardIoRate(0f);        // not charged for, i think
                product.setStandardStorageRate(0f);   // not charged for, i think
                product.setHighAvailability(false);       // unknown as yet
                for (String region : t.getRegion()) { // list of regions
                    product.setProviderDataCenterId(region); // Needs core change for product.setRegionId(region) 
                    products.add(product);
                }
            }
        }
        catch( Exception e ) {
            throw new CloudException(e);
        }
		finally {
            APITrace.end();
        }
		return products; 
	}

	@Override
	public DatabaseSnapshot getSnapshot(String providerDbSnapshotId) throws CloudException, InternalException {
		return null;
	}

	@Override
	public boolean isSubscribed() throws CloudException, InternalException {
		// TODO Verify that i interpreted this correctly.
		return true;
	}

	@Override
	public Iterable<String> listAccess(String toProviderDatabaseId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseConfiguration> listConfigurations() throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        //getDatabaseEngines()
        //getSupportedVersions(@Nonnull DatabaseEngine forEngine)

		// TODO Auto-generated method stub
	    // DatabaseConfiguration(RelationalDatabaseSupport services, DatabaseEngine engine, String configurationId, String name, String description)
		return null;
	}

    @Override
    public DatabaseConfiguration getConfiguration(String providerConfigurationId) throws CloudException, InternalException {




        // TODO Auto-generated method stub
        return null;
    }

	@Override
	public Iterable<ResourceStatus> listDatabaseStatus() throws CloudException, InternalException {
    	ProviderContext ctx = provider.getContext();
    	SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

    	ArrayList<ResourceStatus> statuses = new ArrayList<ResourceStatus>();
        java.util.List<DatabaseInstance> resp = null;
        try {
            resp = sqlAdmin.instances().list(ctx.getAccountNumber()).execute().getItems();  // null exception here...
        } catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        } catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
        }

        for (DatabaseInstance instance : resp) {
            ResourceStatus status = new ResourceStatus(instance.getInstance(), instance.getState());
            statuses.add(status);
        }

		return statuses;
	}

	@Override
    public Iterable<Database> listDatabases() throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();
		SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

		ArrayList<Database> list = new ArrayList<Database>();
		java.util.List<DatabaseInstance> resp = null;
		try {
			resp = sqlAdmin.instances().list(ctx.getAccountNumber()).execute().getItems();  // null exception here...
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
		} catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
		}
		try {
		    if (resp != null)
    	        for (DatabaseInstance d : resp) {
	        		String dummy = null;
	        		dummy = d.getProject(); // qa-project-2

	        		dummy = d.getMaxDiskSize().toString(); // 268435456000
	        		//d.getServerCaCert();

	        		Settings s = d.getSettings(); 
	        		//{"activationPolicy":"ON_DEMAND","backupConfiguration":[{"binaryLogEnabled":false,"enabled":false,"id":"f3b56cf1-e916-4611-971c-61b44c045698","kind":"sql#backupConfiguration","startTime":"12:00"}],"ipConfiguration":{"enabled":false},"kind":"sql#settings","pricingPlan":"PER_USE","replicationType":"SYNCHRONOUS","settingsVersion":"1","tier":"D0"}

	        		dummy = s.getActivationPolicy();  // "ON_DEMAND"
	        		//s.getAuthorizedGaeApplications();
	        		java.util.List<BackupConfiguration> backupConfig = s.getBackupConfiguration();
	        		for (BackupConfiguration backupConfigItem : backupConfig) {
	        			System.out.println(backupConfigItem.getId()); //f3b56cf1-e916-4611-971c-61b44c045698
	        			System.out.println(backupConfigItem.getKind()); //sql#backupConfiguration
	        			System.out.println(backupConfigItem.getStartTime()); // 12:00
	        			System.out.println(backupConfigItem.getBinaryLogEnabled());  // false
	        			System.out.println(backupConfigItem.getEnabled());  // false

	        		}
	        		java.util.List<DatabaseFlags> dbfl = s.getDatabaseFlags();
	        		if (dbfl != null)
		        		for (DatabaseFlags dbflags : dbfl) {
		        			System.out.println(dbflags.getName() + " = " + dbflags.getValue());
		        		}

	        		//s.getIpConfiguration();

	        		LocationPreference lp = s.getLocationPreference();
	        		if (lp != null)
	        			lp.getZone();
	        		dummy = s.getPricingPlan();  // PER_USE or PACKAGE
	        		dummy = s.getReplicationType(); // SYNCHRONOUS
	        		dummy = s.getSettingsVersion().toString(); // 0
	        		dummy = s.getTier(); // D0


	        		Database database = new Database();

	        		database.setAdminUser("root");
	        		Long currentBytesUsed = d.getCurrentDiskSize();
	        		if (currentBytesUsed != null) {
		        		int currentGBUsed = (int) (currentBytesUsed / 1073741824);
		        		database.setAllocatedStorageInGb(currentGBUsed);
	        		}
	        		//database.setConfiguration(configuration);
	        		//database.setCreationTimestamp(creationTimestamp);

	        		String googleDBState = d.getState(); // PENDING_CREATE
	        		if (googleDBState.equals("RUNNABLE")) {
	            		database.setCurrentState(DatabaseState.AVAILABLE);
	        		} else if (googleDBState.equals("SUSPENDED")) {
	            		database.setCurrentState(DatabaseState.SUSPENDED);
	        		} else if (googleDBState.equals("PENDING_CREATE")) {
	            		database.setCurrentState(DatabaseState.PENDING);
	        		} else if (googleDBState.equals("MAINTENANCE")) {
	            		database.setCurrentState(DatabaseState.MAINTENANCE);
	        		} else if (googleDBState.equals("UNKNOWN_STATE")) {
	            		database.setCurrentState(DatabaseState.UNKNOWN);
	        		} 

	        		if (d.getDatabaseVersion().equals("MYSQL_5_5"))
	        			database.setEngine(DatabaseEngine.MYSQL); //  MYSQL55
	        		else if (d.getDatabaseVersion().equals("MYSQL_5_6"))
	        			database.setEngine(DatabaseEngine.MYSQL); // MYSQL56

	        		//database.setHostName(d.getIpAddresses().get(0).getIpAddress()); // BARFS
	        		database.setHostPort(3306);  // Default mysql port
	        		database.setName(d.getInstance()); // dsnrdbms317
	        		database.setProductSize(s.getTier()); // D0
	        		database.setProviderDatabaseId(d.getInstance()); // dsnrdbms317
	        		database.setProviderOwnerId(provider.getContext().getAccountNumber()); // qa-project-2
	        		database.setProviderRegionId(d.getRegion()); // us-central
                    //database.setProviderDataCenterId(providerDataCenterId);

                    //database.setHighAvailability(highAvailability);
                    //database.setMaintenanceWindow(maintenanceWindow);
	        		//database.setRecoveryPointTimestamp(recoveryPointTimestamp);
	        		//database.setSnapshotRetentionInDays(snapshotRetentionInDays);
	        		//database.setSnapshotWindow(snapshotWindow);

					list.add(database);

    	        }
	        return list;
		} catch (Exception e) {
			System.out.println("EXCEPTION " + e);
			e.printStackTrace();
		}
		return null;
	}

	@Override
	public Collection<ConfigurationParameter> listParameters(String forProviderConfigurationId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		return null;
	}

	@Override
	public Iterable<DatabaseSnapshot> listSnapshots(String forOptionalProviderDatabaseId) throws CloudException, InternalException {
        ArrayList<DatabaseSnapshot> snapshots = new ArrayList<DatabaseSnapshot>();
		return snapshots;
	}

	@Override
	public void removeConfiguration(String providerConfigurationId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void removeDatabase(String providerDatabaseId) throws CloudException, InternalException {
		ProviderContext ctx = provider.getContext();
		SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

		try {
			sqlAdmin.instances().delete(ctx.getAccountNumber(), providerDatabaseId).execute();
		} catch (IOException e) {
			if (e.getClass() == GoogleJsonResponseException.class) {
				GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
				throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
			} else
				throw new CloudException(e);
		} catch (Exception ex) {
            throw new CloudException("Access denied.  Verify GCE Credentials exist.");
		}
	}

	@Override
	public void removeSnapshot(String providerSnapshotId) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void resetConfiguration(String providerConfigurationId, String... parameters) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void restart(String providerDatabaseId, boolean blockUntilDone) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

	    try {
	        InstancesRestartResponse op = sqlAdmin.instances().restart(ctx.getAccountNumber(), providerDatabaseId).execute();
	        // appears to be instantanious... so no need for blockUntilDone
    	} catch (IOException e) {
            if (e.getClass() == GoogleJsonResponseException.class) {
                GoogleJsonResponseException gjre = (GoogleJsonResponseException)e;
                throw new GoogleException(CloudErrorType.GENERAL, gjre.getStatusCode(), gjre.getContent(), gjre.getDetails().getMessage());
            } else
                throw new CloudException(e);
        }
	}

	@Override
	public void revokeAccess(String providerDatabaseId, String sourceCide) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public void updateConfiguration(String providerConfigurationId, ConfigurationParameter... parameters) throws CloudException, InternalException {
		// TODO Auto-generated method stub
		
	}

	@Override
	public DatabaseSnapshot snapshot(String providerDatabaseId, String name) throws CloudException, InternalException {
	    throw new InternalException("Take snapshot not supported");
	}

    @Override
    public RelationalDatabaseCapabilities getCapabilities() throws InternalException, CloudException {
        return new GCERelationalDatabaseCapabilities(provider);
    }

    @Deprecated
    public Iterable<DatabaseProduct> getDatabaseProducts( DatabaseEngine forEngine ) throws CloudException, InternalException {
        return listDatabaseProducts(forEngine);
    }

    @Override
    public DatabaseBackup getBackup(String providerDbBackupId) throws CloudException, InternalException {
        // TODO candidate for cache optimizating.
        Iterable<DatabaseBackup> backupList = listBackups(null);
        for (DatabaseBackup backup : backupList) 
            if (providerDbBackupId.equals(backup.getProviderBackupId()))
                return backup;

        return null;
    }

    @Override
    public Iterable<DatabaseBackup> listBackups( String forOptionalProviderDatabaseId ) throws CloudException, InternalException {
        ArrayList<DatabaseBackup> backups = new ArrayList<DatabaseBackup>();
        if (forOptionalProviderDatabaseId == null) {
            Iterable<Database> dataBases = listDatabases();
            for (Database db : dataBases) 
                backups.addAll(getBackupForDatabase(db.getProviderDatabaseId()));
        } else 
            backups = getBackupForDatabase(forOptionalProviderDatabaseId);

        return backups;
    }

    @Override
    public String createFromBackup( String dataSourceName, String providerDatabaseId, String providerDbBackupId, String productSize, String providerDataCenterId, int hostPort ) throws CloudException, InternalException {
        
        // TODO Auto-generated method stub
        
        // this needs a method...
        // sqlAdmin.instances().restoreBackup(arg0, arg1, arg2, arg3)

        return null;
    }

    @Override
    public boolean removeBackup( String providerBackupId ) throws CloudException, InternalException {
        // TODO Auto-generated method stub
        return false;
    }


    public ArrayList<DatabaseBackup> getBackupForDatabase(String forDatabaseId) throws CloudException, InternalException {
        ProviderContext ctx = provider.getContext();
        SQLAdmin sqlAdmin = provider.getGoogleSQLAdmin();

        ArrayList<DatabaseBackup> backups = new ArrayList<DatabaseBackup>();

        Database db = getDatabase(forDatabaseId);
        
        BackupRunsListResponse backupRuns = null;
        try {
            backupRuns = sqlAdmin.backupRuns().list(ctx.getAccountNumber(), forDatabaseId, "").execute();
        }
        catch( Exception e ) {
            throw new CloudException(e);
        }
        try {
            for (BackupRun backupItem : backupRuns.getItems()) {
                DatabaseBackup backup = new DatabaseBackup();
                String instance = backupItem.getInstance();
                backup.setProviderDatabaseId(instance);
                backup.setAdminUser(db.getAdminUser());
                backup.setProviderOwnerId(db.getProviderOwnerId());
                backup.setProviderRegionId(db.getProviderRegionId());

                String status = backupItem.getStatus();
                if (status.equals("SUCCESSFUL")) {
                    backup.setCurrentState(DatabaseBackupState.AVAILABLE);
                    backup.setBackupTimestamp(backupItem.getStartTime().getValue());
                } else {
                    backup.setCurrentState(DatabaseBackupState.valueOf(status)); 
                    // this will likely barf first time it gets caught mid backup, 
                    // but with backup windows being 4 hours... will have to wait to catch this one...
                    backup.setBackupTimestamp(backupItem.getDueTime().getValue());
                }
                backup.setProviderBackupId(instance + "_" + backup.getBackupTimestamp()); // artificial concat of db name and timestamp
                OperationError error = backupItem.getError(); // null
                if (error != null) 
                    backup.setCurrentState(DatabaseBackupState.ERROR);


                // db.isHighAvailability();
                // Unknown what to do with
                //String config = backup.getBackupConfiguration(); // 991a6ae6-17c7-48a1-8410-9807b8e3e2ad
                //Map<String, Object> keys = backup.getUnknownKeys();
                //int retentionDays = db.getSnapshotRetentionInDays();
                //String kind = backup.getKind(); // sql#backupRun
                //snapShot.setStorageInGigabytes(storageInGigabytes);  // N.A.

                backups.add(backup);
            }
        }
        catch( Exception e ) {
            throw new InternalException(e);
        }

        return backups;  
    }


    @Deprecated
    public boolean isSupportsFirewallRules() {
        boolean supportsFirewallRules = false;
        try {
            supportsFirewallRules = getCapabilities().isSupportsFirewallRules();
        } catch( Exception e ) {  } // ignore

        return supportsFirewallRules;
    }

    @Deprecated
    public boolean isSupportsHighAvailability() throws CloudException, InternalException {
        // https://cloud.google.com/developers/articles/building-high-availability-applications-on-google-compute-engine
        /*
         * Database db = getDatabase(forDatabaseId);
         * db.isHighAvailability()
         */
        return true;
    }

    @Deprecated
    public boolean isSupportsLowAvailability() throws CloudException, InternalException {
        boolean supportsLowAvailability = false;
        try {
            supportsLowAvailability = getCapabilities().isSupportsLowAvailability();
        } catch( Exception e ) {  } // ignore

        return supportsLowAvailability;
    }

    @Deprecated
    public boolean isSupportsMaintenanceWindows() {
        boolean supportsMaintenanceWindows = false;
        try {
            supportsMaintenanceWindows = getCapabilities().isSupportsMaintenanceWindows();
        } catch( Exception e ) {  } // ignore

        return supportsMaintenanceWindows;
    }

    @Deprecated
    public boolean isSupportsSnapshots() {
        /*
         * Google Cloud SQL backups are taken by using FLUSH TABLES WITH READ LOCK to create a snapshot. 
         * This will prevent writes, typically for a few seconds. Even though the instance remains online, 
         * and reads are unaffected, it is recommended to schedule backups during the quietest period for 
         * your instance. If there is a pending operation at the time of the backup attempt, Google Cloud 
         * SQL retries until the backup window is over. Operations that block backup are long-running 
         * operations such as import, export, update (e.g., for an instance metadata change), and 
         * restart (e.g., for an instance restart).
         */
        boolean supportsSnapshots = false;
        try {
            supportsSnapshots = getCapabilities().isSupportsSnapshots();
        } catch( Exception e ) {  } // ignore

        return supportsSnapshots;
    }

    @Deprecated
    public String getProviderTermForDatabase(Locale locale) {
        String providerTermForDatabase = null;
        try {
            providerTermForDatabase = getCapabilities().getProviderTermForDatabase(locale);
        } catch( Exception e ) {  } // ignore

        return providerTermForDatabase;
    }

    @Deprecated
    public String getProviderTermForSnapshot(Locale locale) {
        String providerTermForSnapshot = null;
        try {
            providerTermForSnapshot = getCapabilities().getProviderTermForSnapshot(locale);
        } catch( Exception e ) {  } // ignore

        return providerTermForSnapshot;
    }

}

