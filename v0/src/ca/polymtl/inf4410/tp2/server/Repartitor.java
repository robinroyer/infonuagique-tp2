package ca.polymtl.inf4410.tp2.server;

import java.io.BufferedReader;
import java.io.FileInputStream;
import java.io.FileReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.net.InetAddress;
import java.nio.charset.Charset;
import java.rmi.AccessException;
import java.rmi.NotBoundException;
import java.rmi.RemoteException;
import java.rmi.registry.LocateRegistry;
import java.rmi.registry.Registry;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.concurrent.Semaphore;

import ca.polymtl.inf4410.tp2.shared.CalculousServerInterface;

public class Repartitor {

	private static final String PATH_CONFIG_CALCULOUS_SERVERS = "./config/servers.config";

	private static final int SEM_C_NUMBER_OF_TOKEN = 1;
	private static final int SEM_TVC_NUMBER_OF_TOKEN = 1;
        private static final String SAFE_ARGUMENT = "-S";
	/**
	 * Error exit IO code
	 */
	private static final int ERROR_IO = -10;

	/**
	 * Error exit RMI code
	 */
	private static final int ERROR_RMI = -20;

	/**
	 * Error exit not bound code
	 */
	private static final int ERROR_NOT_BOUND = -30;

	/**
	 * Error exit access code
	 */
	private static final int ERROR_ACCESS = -40;

	/**
	 * Minimum number of unit operation to do by a server (q value)
	 */
	private static final int MINIMUM_NUMBER_OF_OPERATIONS = 2;

	/**
	 * ArrayList to store the calculations to do
	 */
	private ArrayList<String> calculations;	

	/**
	 * Semaphore to provide access to the calculations structure
	 */
	private Semaphore calculationsSemaphore;

	/**
	 * ArrayList to store the calculations to verify
	 */
	private ArrayList<Task> toVerifyCalculations;

	/**
	 * Hashmap to match a server with its information
	 */
	private HashMap<CalculousServerInterface, CalculousServerInformation> serverInformations;

	/**
	 * Semaphore to provide access to the calculations to verify structure
	 */
	private Semaphore toVerifyCalculationsSemaphore;

	/**
	 * Boolean to know if the repartitor is in safe mode or not
	 */
	private boolean safeMode;

	/**
	 * Boolean to control threads ending
	 */
	private boolean threadsShouldEnd = false;

        /**
         * Arrayslist of our different thread
         */
        private ArrayList<Thread> threads;
        
        /**
         * GlobalResult is an array of size 2 in order to be able to give it by
         * ref to threads
         * index 0 => the result
         * index 1 => number of operation stacked in result
         */
        private int[] globalResult;
        
        /**
         * Semaphore protecting globalresult
         */
        private Semaphore globalResultLock;
        
        /**
         * number of operation provided to the repartitor
         */
        private int operationNumber;
        
        
private ArrayList<CalculousServerInterface> CalculousServeurs;

	/**
	 * The distant servers used for our project
	 */
//	private CalculousServerInterface distantServerStub = null;

	/**
	 * Public constructor to create a Repartiteur instance.
	 * 
	 */
	public Repartitor(boolean isSafe) {

                safeMode = isSafe;
            
		calculationsSemaphore = new Semaphore(SEM_C_NUMBER_OF_TOKEN);
		calculations = new ArrayList<>();

		//toVerifyCalculationsSemaphore = new Semaphore(SEM_TVC_NUMBER_OF_TOKEN);
		//toVerifyCalculations = new ArrayList<>();

		serverInformations = new HashMap<>();
		CalculousServeurs = new ArrayList<>();
                
                threads = new ArrayList<>();

		globalResult = new int[0];
                globalResult[0] = 0;

		if (System.getSecurityManager() == null) {
			System.setSecurityManager(new SecurityManager());
		}
	}

	/**
	 * Main point of the program
	 * 
	 * @param args
         * @throws java.lang.InterruptedException
	 */
	public static void main(String[] args) throws InterruptedException {

                // checking safe mode
		boolean isSafe = false;
		if (args.length > 1 && args[0].equals(SAFE_ARGUMENT)) {
			isSafe = true;
			System.out.println("Safe mode detecte.");
		} else {
			System.out.println("Mode non Safe -> Les calculs ne seront pas verifies.");
		}

                // Creation of the repartitor instance
		Repartitor repartiteur = new Repartitor(isSafe);
		// Start repartitor's job
		repartiteur.runRepartitor();
	}

	/**
	 * Main private method to run the repartitor
	 */
	private void runRepartitor() throws InterruptedException {

		String commande = null;
		String commandes[] = null;

		BufferedReader reader = new BufferedReader(new InputStreamReader(System.in));

		System.out.println("Chargement des serveurs ...");
		loadServerFromConfigFile();
		
		System.out.println("Attente des commandes ...");
		try {
			while ((commande = reader.readLine()) != null) {
				commandes = commande.split(" ");
				String command = commandes[0];
				String filename = commandes[1];

				if (command.equals("compute")) {
					try {
						parseCalculousFileToCalculous(filename);
					} catch (IOException e) {}
					startThreadsThenJoin();					
				}
			}
		} catch (IOException e) {
			System.err.println("Erreur dans la lecture du flux d'entree sortie.");
			e.printStackTrace();
			System.exit(ERROR_IO);
		}
	}

	/**
	 * Load all the configuration of the calculous servers
	 */
	private void loadServerFromConfigFile() {
		try {
			FileReader fr = new FileReader(PATH_CONFIG_CALCULOUS_SERVERS);
			BufferedReader br = new BufferedReader(fr);
			String line = null;

			while ((line = br.readLine()) != null) {
				String[] array = line.split(" ");
				CalculousServerInterface csi = loadServerStub(array[0],
						Integer.parseInt(array[1]));
				CalculousServeurs.add(csi);
				serverInformations.put(csi, new CalculousServerInformation(
						MINIMUM_NUMBER_OF_OPERATIONS));
			}
			br.close();
		} catch (IOException e) { // TODO : gestion exception proprement
			e.printStackTrace();
		}
	}

	/**
	 * Private method to load the server
	 * 
	 * @param hostname
	 * @return
	 */
	private CalculousServerInterface loadServerStub(String hostname, int port) {
		CalculousServerInterface stub = null;

		try {
			Registry registry = LocateRegistry.getRegistry(hostname, port);
			stub = (CalculousServerInterface) registry.lookup(hostname);
			try {
				System.out.println(InetAddress.getLocalHost().getHostName());
			} catch (Exception e) {
				e.printStackTrace();
			}
		} catch (NotBoundException e) {
			System.err.println("Erreur: Le nom  " + e.getMessage()
					+ "  n est pas defini dans le registre.");
			System.exit(ERROR_NOT_BOUND);
		} catch (AccessException e) {
			System.err.println("Erreur: " + e.getMessage());
			System.exit(ERROR_ACCESS);
		} catch (RemoteException e) {
			System.err.println("Erreur: " + e.getMessage());
			System.exit(ERROR_RMI);
		}

		return stub;
	}

	private void startThreadsThenJoin() throws IOException, InterruptedException {
		if (safeMode){
                    for (CalculousServerInterface server : CalculousServeurs) {
			SafeRepartitorThread thread = 
                                new SafeRepartitorThread( this, server, calculations,
                                        calculationsSemaphore, globalResult, globalResultLock );
			threads.add( thread );
                        thread.start();
                    }
                
                }else{
                    //unsafe mode
                    // => SafeRepartitorThread 
//                    for (CalculousServerInterface server : CalculousServeurs) {
//			UnsafeRepartitorThread thread = new UnsafeRepartitorThread(this, server);
//			threads.add( thread );
//                        thread.start();
//                    }                    
                }
                
                Thread coordinationThread = new CoordinateThread(this, globalResult, globalResultLock, operationNumber);
                        
                // SYNCHRONISATION
                coordinationThread.join();
                for (Thread thread : threads) {
                    thread.join();
                }
                
                System.out.println("Resultats des calculs => " + globalResult[0]);
      	}

	/**
	 * Private method to store the initial calculations to do
	 * 
	 * @param filename
	 * @throws IOException
	 */
	private void parseCalculousFileToCalculous(String filename) throws IOException {
		// Some declarations ...
		String line = null;
		FileInputStream fis = new FileInputStream(filename);
		InputStreamReader isr = new InputStreamReader(fis,
				Charset.forName("UTF-8"));
		BufferedReader br = new BufferedReader(isr);

		// Add each line of the file to the datastructure
		while ((line = br.readLine()) != null) {
			calculations.add(line);
		}

		// Close the buffer
		br.close();
                operationNumber = calculations.size();
	}

//	public Task getTasksToVerify(CalculousServerInterface stub)
//			throws NoMoreWorkToVerifyException {
//
//		// If the map is empty we don't need to do the job
//		if (toVerifyCalculations.isEmpty()) {
//			throw new NoMoreWorkToVerifyException();
//		}
//
//		// Protect the datastructure by using a semaphore
//		try {
//			toVerifyCalculationsSemaphore.acquire(1);
//		} catch (InterruptedException e) {
//			System.err.println("Probleme de semaphore.");
//		}
//
//		Task taskToVerify = null;
//		for (Task task : toVerifyCalculations) {
//
//			if (!stub.equals(task.getAuthor())) {
//				taskToVerify = task;
//			} else {
//				continue;
//			}
//		}
//
//		// Case all calculous to verify have been done by the available server
//		if (taskToVerify.equals(null)) {
//			throw new NoMoreWorkToVerifyException();
//		}
//
//		// Release the token to the semaphore
//		toVerifyCalculationsSemaphore.release(1);
//
//		return taskToVerify;
//	}

	/**
	 * Get some calculous from the calculous datastructure
	 * 
	 * @return a minimum number of calculous to do
	 */
//	public String[] getSomeCalculous() throws NoMoreWorkException {
//
//		// If the data structure is empty we don't need to do the job
//		if (calculations.isEmpty()) {
//			throw new NoMoreWorkException();
//		}
//
//		// Protect the datastructure by using semaphore
//		try {
//			calculationsSemaphore.acquire(1);
//		} catch (InterruptedException e) {
//			System.err.println("Probleme de semaphore.");
//		}
//
//		String[] calculous = new String[3];
//		int compteur = 0;
//
//		// Store the calculous information into an array
//		Iterator<String> i = calculations.iterator();
//		while (i.hasNext()) {
//
//			// Get the calculous from the calculations datastructure
//			calculous[compteur] = i.next();
//			// Remove it from the calculations datastructure
//			i.remove();
//
//			// Set the apropriate number of minimum operations to do
//			if (compteur != MINIMUM_NUMBER_OF_OPERATIONS) {
//				compteur++;
//			} else {
//				break;
//			}
//		}
//
//		// Release the token from the semaphore
//		calculationsSemaphore.release(1);
//
//		return calculous;
//	}

//	public void addCalculousToVerify(CalculousServerInterface stub,
//			String[] calculous, int result) {
//		toVerifyCalculations.add(new Task(stub, calculous));
//	}

//	public void addCalculous(String[] calculous) {
//		try {
//			toVerifyCalculationsSemaphore.acquire(1);
//		} catch (InterruptedException e) {
//			// TODO : gerer
//			e.printStackTrace();
//		}
//
//		for (int i = 0; i < calculous.length; i++) {
//			calculations.add(calculous[i]);
//		}
//
//		toVerifyCalculationsSemaphore.release(1);
//	}

//	public void updateCapacity(CalculousServerInterface serverStub, int value) {
//		// recuperation de l'instance information relative au bon stub
//		CalculousServerInformation csi = serverInformations.get(serverStub);
//		csi.setCapacity(csi.getCapacity() + value);
//	}

//	public void updateOverloadedSituation(CalculousServerInterface serverStub,
//			boolean b) {
//		// recuperation de l'instance information relative au bon stub
//		CalculousServerInformation csi = serverInformations.get(serverStub);
//		csi.setPreviousCalculHasBeenOverloaded(b);
//	}

//	public void removeTaskToVerify(Task task) {
//		toVerifyCalculations.remove(task);
//	}

	public boolean isSafeMode() {
		return this.safeMode;
	}
        
        public boolean threadsShouldContinue(){
            return !threadsShouldEnd;
        }
        
        public void stopTheThreads(){
                threadsShouldEnd = false;
        }
}
