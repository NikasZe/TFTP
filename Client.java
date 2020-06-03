/* Autorius: Nikodemas Žeruolis,
 * Informatikos 1 gr., 2 kursas
 * Programa: TFTP protokolo klientas, galintis vykdyti 'get' funkcija
 */

import java.io.ByteArrayOutputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.net.*;
import java.util.Arrays;
import java.util.Scanner;

public class Client {

    public static int port = 69;

    public static void main(String []args) throws IOException{

        Scanner scan = new Scanner(System.in);
        //Ivedamas serverio IP adresas ir norimo gauti failo pavadinimas
        System.out.println("Enter the hosts IP address: ");
        String host = scan.nextLine();
        System.out.println("Enter file name: ");
        String fileName = scan.nextLine();
        if(validHost(host)){
            DatagramSocket clientSocket = new DatagramSocket();
            get(fileName, clientSocket, host);
        }else System.out.println("Invalid host. Exiting.");
    }
    //Tikrinama, ar duotas Stringas yra tinkamas InetAddress adresas
    private static boolean validHost(String hName){
        boolean valid=true;
        try{
            InetAddress hostAddress = InetAddress.getByName(hName);
        }catch(UnknownHostException e){
            valid=false;
            System.out.println("Invalid host");
        }
        return valid;
    }

    //Funkcija, skirta gauti norima faila, kaip argumenta paimanti gauti norimo failo pavadinima, kliento socket'a ir host'o IP adresa
    private static void get(String fName, DatagramSocket clientSocket, String hostName) throws IOException {

        int hostPort = port, dataSize = 0;

        boolean display = false;

        FileOutputStream myFile = null;

        //Gaunamas IP adresas ir pasinaudojus createRQ funkcija gaunamas baitu stream'as su kuriuo bus RRQ paketas
        InetAddress hostAddress = InetAddress.getByName(hostName);
        ByteArrayOutputStream reqBytes = createRQ(fName);

        //Apibreziami 3 byte tipo kintamieji paketu kurimui
        byte[] respData = new byte[516];
        byte[] reqData = new byte[516];
        byte[] fileData = new byte[512];

        //Sukuriamas paketas gautai informacijai
        reqData = reqBytes.toByteArray();
        DatagramPacket recPacket = new DatagramPacket(respData, respData.length);

        //Sukuriamas ir issiunciamas RRQ paketas ir nustatomas 5 sekundziu timeout laikas
        DatagramPacket datagramRRQ_Packet =
                new DatagramPacket(reqData,reqBytes.size(),hostAddress,hostPort);

        clientSocket.send(datagramRRQ_Packet);

        clientSocket.setSoTimeout(5000);

        try{
            do{
                clientSocket.receive(recPacket);
                hostPort = recPacket.getPort();

                byte[] opcode;
                byte[] blockNumber;

                opcode = Arrays.copyOfRange(recPacket.getData(), 0, 2);
                //Pagal tai, koks gauto paketo opcode, atliekami atitinkami veiksmai
                if(opcode[1] == 5){
                    printError(recPacket);
                }else if(opcode[1] == 3){
                    clientSocket.setSoTimeout(600000);
                    display = true;
                    blockNumber = Arrays.copyOfRange(recPacket.getData(), 2, 4);
                    if(myFile == null)
                        myFile = new FileOutputStream(fName);
                    //I faila irasoma informacija nuo 4 baito, nes pirmi 4 uzimti opcode ir block#
                    fileData = Arrays.copyOfRange(recPacket.getData(), 4, recPacket.getLength());
                    myFile.write(fileData);

                    //Irasoma kiek baitu yra pervesta
                    dataSize+=recPacket.getLength();

                    //Sukuriamas baitu stream'as patvirtinimo paketui pagal TFTP protokolui
                    ByteArrayOutputStream ackBytes = new ByteArrayOutputStream();
                    ackBytes.write(0);
                    ackBytes.write(4);
                    ackBytes.write(blockNumber);
                    for(int i = ackBytes.size();i<516;i++){
                        ackBytes.write(0);
                    }

                    //Sukuriamas ir issiunciamas ACK paketas
                    DatagramPacket ackPacket =
                            new DatagramPacket(ackBytes.toByteArray(),ackBytes.size(),hostAddress,hostPort);
                    clientSocket.send(ackPacket);

                }

            }while((recPacket.getLength()==516));
            if(myFile!=null)
                myFile.close();
            if(display){
                //Patvirtinamas failo parsiuntimas ir parodomas failo dydis
                System.out.println("Transfer successful. Transferred "+dataSize+" bytes.");
            }
        }catch(SocketTimeoutException s){
            System.out.println("Transfer timeout.");
        }
    }

    //Jei grazinto paketas yra error tipo, nuskaitomas error kodas ir isspausdinamas jo pranesimas
    private static void printError(DatagramPacket recPacket) {
        byte[] errorCode = Arrays.copyOfRange(recPacket.getData(), 2, 4);
        String errorMsg =
                new String(recPacket.getData(), 4, recPacket.getLength()-5);
        System.out.println("Error code " + errorCode[1]+ ": " + errorMsg);
    }
    //Funkcija, irasanti i masyva RRQ paketo informacija pagal TFTP protokolo formatu reikalavimus
    private static ByteArrayOutputStream createRQ(String fName) throws IOException {
        /*
        *Pirmi 2 baitai yra 01 opcode
        *Toliau eina failo pavadinimas netascii baitais, o po jo 0
        *Galiausiai eina rezimas 'mode' nustatytas i 'octet' ir baigiamasis 0 baitas
        */
        ByteArrayOutputStream rqStream = new ByteArrayOutputStream();
        rqStream.write(0);
        rqStream.write(1);
        rqStream.write(fName.getBytes());
        rqStream.write(0);
        rqStream.write("octet".getBytes());
        rqStream.write(0);
        for(int i = rqStream.size();i<516;i++){
            rqStream.write(0);
        }

        return rqStream;
    }
}