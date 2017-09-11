package aucado;

import static java.lang.System.exit;

import java.io.*;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.util.Map;
import java.util.TreeMap;
import java.util.Scanner;
import javax.json.Json;
import javax.json.JsonObject;
import javax.json.JsonArray;
import javax.json.JsonReader;
import javax.ws.rs.client.Client;
import javax.ws.rs.client.ClientBuilder;

public class EtsyConnector {
    // API Key to connect to etsy openapi server
    static String EtsyApiKey = "venpzdgm8quakloa1t4nt9yx";
    static String EtsyListingUri = "https://openapi.etsy.com/v2/shops/%d/listings/active/?api_key=";
    static String ListingDirectory = "/var/tmp/listings";
    static String emptyListing = "{\"count\":0}";

    /**
     * Class that compares existing listings from file to latest listings and outputs the differences
     */
    public class ListingSynchronize {

        private String outputDirectory;
        Map<Integer, String> oldListings = new TreeMap<>();
        Map<Integer, String> newListings = new TreeMap<>();
        String newListingResult;

        /**
         * Constructor
         *
         * @param outputDirectory Directory where shop id files are stored
         */
        public ListingSynchronize(String outputDirectory) {

            this.outputDirectory = outputDirectory;
        }

        /**
         * Output differences since last sync and update last listings file
         *
         * @param shopId Shop to synchronize
         */
        public void sync(int shopId) {
            getOldListing(shopId);
            getNewListing(shopId);
            compare(shopId);
            oldListings.clear();
            newListings.clear();

            try {
            FileOutputStream os = new FileOutputStream(outputDirectory + "/" + shopId, false);
            PrintStream ps = new PrintStream(os);
            ps.print(newListingResult);
            os.close();
            } catch(FileNotFoundException e) {
                System.err.println("Error: Unable to write listing file for shop id " + shopId);
            }
            catch(IOException e) {
                System.err.println("Error: Unable to write listing file for shop id " + shopId);
            }

        }

        /**
         * Retrieve the old listings stored locally 
         * @param shopId Shop to retrieve
         */
        private void getOldListing(int shopId) {
            String existing = emptyListing;

            try {
                existing = new String(Files.readAllBytes(Paths.get(outputDirectory + "/" + shopId)));
            } catch (IOException e){
                System.err.println("Error: Unable to read shop listing file for shop id " + shopId);
                System.err.println("Creating new empty listing file for intial comparison for shop " + shopId);

                try {
                    FileOutputStream os = new FileOutputStream(outputDirectory + "/" + shopId, false);
                    PrintStream ps = new PrintStream(os);
                    ps.print(emptyListing);
                    os.close();
                } catch(FileNotFoundException nf) {
                    System.err.println("Error: Unable to write listing file for shop id " + shopId);
                    return;
                }
                catch(IOException io) {
                    System.err.println("Error: Unable to write listing file for shop id " + shopId);
                    return;
                }
            }    
            JsonReader reader = Json.createReader(new StringReader(existing));
            JsonObject object = reader.readObject();
            reader.close();

            // put listing id and descripton into a map for comparison
            int count = object.getInt("count");
            JsonArray listings = object.getJsonArray("results");
            for (int idx = 0; idx < count; ++idx) {
                int id = listings.getJsonObject(idx).getInt("listing_id");
                String description = listings.getJsonObject(idx).getJsonString("description").getString();
                oldListings.put(id, description);
            }
        }

        /**
         * Retrieve the new listings from etsy
         * @param shopId Shop to retrieve
         */
        private void getNewListing(int shopId)
        {
            String getListingUri = EtsyListingUri + EtsyApiKey;
            getListingUri = String.format(getListingUri, shopId);
            Client client = ClientBuilder.newClient();
            newListingResult = client.target(getListingUri).request().get(String.class);

            JsonReader reader = Json.createReader(new StringReader(newListingResult));
            JsonObject object = reader.readObject();
            reader.close();

            // put listing id and descripton into a map for comparison
            int count = object.getInt("count");
            JsonArray listings = object.getJsonArray("results");
            for (int idx = 0; idx < count; ++idx) {
                int id = listings.getJsonObject(idx).getInt("listing_id");
                String description = listings.getJsonObject(idx).getJsonString("description").getString();
                newListings.put(id, description);
            }
        }
        /** Compare the two maps and output the differences
         *  
         * @param shopId Shop being sync'd
         */
        private void compare(int shopId) {
            boolean changed = false;

            System.out.println("Shop ID " + shopId);
            for (Map.Entry<Integer, String> entry : newListings.entrySet()) {
                if (oldListings.containsKey(entry.getKey())) {
                    // remove old listings that are in the new
                    oldListings.remove(entry.getKey());
                } else {
                    // output new listings that are not in the old
                    System.out.println("+ added listing " + entry.getKey() + " " + newListings.get(entry.getKey()));
                    changed = true;
                }
            }
            if (oldListings.isEmpty() && !changed) {
                System.out.println("No changes since last sync.");
            } else {
                // output remaining removed listings that are still in the old
                for (Map.Entry<Integer, String> entry : oldListings.entrySet()) {
                    System.out.println("- removed listing " + entry.getKey() + " " + entry.getValue());
                }
            }
            System.out.println();
        }
    }

    /**
     * Synchronize all shop ids from input
     * @param listingDirectory The directory containing the shop listing files
     */
    private void synchronizeListings(String listingDirectory) {
        Scanner stdin = new Scanner(System.in);
        int lineCount = 0;

        ListingSynchronize ls = new ListingSynchronize(listingDirectory);

        while(stdin.hasNextLine()) {
            ++lineCount;
            try {
                int shopId = Integer.parseInt(stdin.nextLine());
                ls.sync(shopId);
            } catch(NumberFormatException e) {
                System.err.println("Invalid non-integer shop id discarded from input file at line " + lineCount +".");
            }
        }
    }

    /**
     *  Print simple usage message
     */
    private static void usage() {
        System.out.println("\nUsage: command <ShopIds> <Listings>\n");
        System.out.println("  ShopIds  - The fully qualified name of input data file of Shop Ids or \"\" to use stdin.");
        System.out.println("             Each line of the input file consists of a Shop Id. For example \"1598799\"");
        System.out.println("  Listings - Name of an existing output directory containing shop listings. The default is \"/var/tmp/listings\".\n");
        exit(-1);
    }

    private void start(String[] args)
    {
        String listingDirectory = ListingDirectory;

        if (args.length > 0) {
            // validate input file exists if given otherwise read from stdin
            try {
                if(args[0].length() != 0) {
                    Path path = Paths.get(args[0]);
                    if(path.isAbsolute()) {
                        System.setIn(new java.io.FileInputStream((args[0])));
                    }
                }
            } catch(FileNotFoundException e) {
                System.err.println("Error: Unable to open input file.");
                usage();
            }
            // validate output directory exists 
            if(args.length > 1 && args[1].length() != 0) {
                Path path = Paths.get(args[0]);
                if(Files.exists(path)) {
                    System.err.println("Error: Output directory must be an existing directory");
                    usage();
                }
            }

            // do the work
            System.err.println("Synchronizing Shop Ids");
            synchronizeListings(listingDirectory);

        } else {
            System.out.println("Error: More arguments must be specified.");
            usage();
        }
    }

    public static void main(String[] args) {
        try {
            EtsyConnector ec = new EtsyConnector();
            ec.start(args);
        }
        catch(Exception e) {
            System.err.println(e.toString());
        }
    }
}
