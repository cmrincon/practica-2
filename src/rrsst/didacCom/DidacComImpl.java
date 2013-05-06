/**
 * Esta clase forma parte del conjunto de pr&#225cticas que se desarrollan en la
 * asignatura Redes y Servicios de Telecomunicaci&#243n relacionadas con la 
 * implementaci&#243n de protocolos did&#225cticos.
 * 
 * Universidad Polit&#233cnica de Madrid
 * EUIT de Telecomunicaci&#243n
 * Diatel (2013)
 */
package rrsst.didacCom;

import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.io.IOException;
import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;
import java.net.SocketException;
import java.util.Arrays;
import rrsst.didacCom.IDUDidacCom.*;

/**
 * Esta clase modela el comportamiento del nivel de comunicaciones DidacCOM.
 * 
 * @author Redes y Servicios de Telecomunicaci&#243n
 * @version 3.0
 *
 */
public class DidacComImpl implements IDidacCom 
{
    private DatagramSocket canal = null;
	
    /**
    * Longitud minima en bytes de una PDU.
    */
    public static final int MIN_LONG_PDU = 2 + GestorHash.LONG_HASH;
	
    /**
    * Valor del campo <i>tipo de PDU</i> que permite hacer referencia a una PDU
    * de datos.
    */
    public static final byte PDU_DATOS = 0;
    
    /**
    * Valor del campo <i>tipo de PDU</i> que permite hacer referencia a una PDU
    * ACK.
    */
    public static final byte PDU_ACK = (byte) 0xFE;
    
    /**
    * Valor del campo <i>tipo de PDU</i> que permite hacer referencia a una PDU
    * NACK.
    */
    public static final byte PDU_NACK = (byte) 0xFF;
	
    /**
    * Longitud m&#225xima en bytes del campo de datos de una PDU de datos del
    * nivel DidacCOM.
    */
    public static final int MAX_DATOS_PDU = IDidacCom.MAX_DATOS;

    /**
    * Longitud m&#225xima en bytes de una PDU del nivel DidacCOM.
    */
    public static final int MAX_LONG_PDU = 2+MAX_DATOS_PDU+GestorHash.LONG_HASH;
	
    /**
    * M&#225ximo n&#250mero de reintentos de env&#237o de una PDU del nivel 
    * DidacCOM.
    */	
    private static final int N_REINTENTOS = 3;

    /**&nbsp&nbsp &#201ste m&#233todo se encarga de recibir una IDU del nivel 
     * superior ClienteUDP, realizar un c&#243digo <i>MD5</i>&nbspe 
     * incorporar&#225  todo a una PDU que enviar&#225 m&#225s tarde a su 
     * entidad par.<br>
     * </br>&nbsp&nbsp Una vez enviada la PDU, esperar&#225 de vuelta la 
     * confirmaci&#243n de que ha llegado correctamente. Si no es as&#237,
     * reintentar&#225 el env&#237o un m&#225ximo de 3 veces. Para cualquier 
     * otro caso, se devuelve una excepci&#243n de tipo <b>ExcepcionDidacCom</b>.
     * 
     * @param idu IDU del nivel superior
     * @throws ExcepcionDidacCom En caso de cualquier error.
     * @throws IllegalArgumentException 
     */
    
    @Override
    public void enviar(IDUDidacCom idu) 
                            throws ExcepcionDidacCom, IllegalArgumentException
    {
        int tries= 0;   //Numero de intentos
        byte PDUType;   //Tipo de PDU que se recibirá
        byte lengthData;//Longitud del campo datos del usuario
        try{
            //Bytes destinados a albergar PDU´s de tipo datos y control
            byte[] PDUControl= new byte [MIN_LONG_PDU];
            byte[] PDUData;
            
            if (canal== null)
            {
                throw new ExcepcionDidacCom("No se ha abierto el canal");   
            }
            //Abrir un stream para copiar info en PDUData más adelante           
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream DataOut = new DataOutputStream(baos);
            
            //Extraer datos de la IDU recibida
            int port = idu.getPuertoUDP();      //Puerto contenido en la IDU
            int longDatos = idu.getLongDatos(); //Longuitud de los datos
            //Byte de datos  en la IDU
            byte[] datos = Arrays.copyOfRange(idu.getDatos(),0,longDatos);      
            String IP = idu.getDirIP();         //String IP en la IDU
            
            /*Primer paso, crear una PDU de datos. Su estructura es la 
            * siguiente:
            * _____________________________________
            * |Tipo PDU|Long.Datos |Datos |  MD5  |
            * |  0x00  |  1 byte   |0-20B | 16 B  |
            * -------------------------------------
            */
            DataOut.write(PDU_DATOS);       //Primer campo (0x00)
            DataOut.write(longDatos);       //Segundo campo
            DataOut.write(datos, 0, longDatos);           //Campo de datos
            //Se copia a PDUData
            PDUData = baos.toByteArray();
            /*El Hash se calculará para todos los campos serializados de la 
            PDU, a la ver que se copia en el Stream DataOut.
            */
            DataOut.write (GestorHash.generarHash(PDUData));
            //Se copia a PDUData
            PDUData = baos.toByteArray();
            do{
                tries++;
                //Se crea un datagrma que envíe la PDU de datos PDUData
                DatagramPacket datagrama = new DatagramPacket(PDUData, 
                            PDUData.length, InetAddress.getByName(IP), port);
                canal.send(datagrama);  //Envío del datagrama con PDUData
                
                /*Segundo paso, crear una PDU de control. Su estructura es la 
                 * siguiente:
                 * _____________________________________
                 * |Tipo PDU|Long.Datos | Datos |  MD5  |
                 * |0xFF/FE |   0x00    |0 bytes| 16 B  |
                 * -------------------------------------
                 * Dicha PDUControl se usará para recibir el ACK/NACK de la 
                 * entidad par.
                */
                //Se crea un datagrama que reciba dicha confirmación
                DatagramPacket ControlDatagram= new DatagramPacket(PDUControl,
                                                                  MIN_LONG_PDU);
                canal.receive(ControlDatagram);
                //Abrir un stream para leer info en PDUControl más adelante  
                ByteArrayInputStream bais= new ByteArrayInputStream(PDUControl);
                DataInputStream DataIn= new DataInputStream(bais);
                /*Primero se comprueba si su longitud es correcta, para después
                 * validar su código hash y más tarde determinar si los campos
                 * tipo y longitud datos son correctos*/
		if(ControlDatagram.getLength()!=MIN_LONG_PDU)
		{
			throw new ExcepcionDidacCom ("La longitud no es la "
                               + "esperada ["+ControlDatagram.getLength()+"].");
		}else{
                    //Si el hash de no es correcto    
                    if (!comprobarHash(PDUControl, MIN_LONG_PDU)) 
                    	throw new ExcepcionDidacCom ("Intento "+tries+" Hash de la "
                            	+ "PDU de control incorrecto");
                    else{ //Si lo es
                        PDUType= DataIn.readByte();        //Copia el tipo de PDU
                        lengthData= DataIn.readByte();     //Copia la long. de datos
                    
                        if ((PDUType!= PDU_NACK)&&(PDUType!= PDU_ACK)) 
                        {
                            throw new ExcepcionDidacCom ("Intento "+tries+". El "
                                + "campo de confirmacion tiene un valor "
                                + "desconocido ["+PDUType+"]");
                    	}else{
                            if(lengthData!= PDU_DATOS) //Valor a tener en PDUControl
                                throw new ExcepcionDidacCom("Intento "+tries+". El "
                                    	+ "valor del campo longitud de datos, no es"
                                    	+ " correcto");
                    	}
                   	
                    }
                	
                }		
		if((tries > N_REINTENTOS) && (PDUType == PDU_NACK))
		{
                    throw new ExcepcionDidacCom("Intento "+tries+". Numero "
                            + "limite de retransmisiones alcanzado");
		}
				
                
                }while ((tries<=N_REINTENTOS) && (PDUType == PDU_NACK));
        
        }catch(IOException e) 
        {
            throw new ExcepcionDidacCom("Ha habido un error");
        }      
    }
        
    /*&nbsp&nbsp &#201ste m&#233todo se encarga de recibir una PDU de la 
     * entidad par, comprobando su c&#243digo <i>MD5</i>&nbsp que 
     * incorporar&#225  todo a una PDU que enviar&#225 m&#225s tarde a su 
     * entidad par.<br>
     * </br>&nbsp&nbsp Una vez procesada la PDU, enviar&#225 de vuelta la 
     * confirmaci&#243n de que ha llegado correctamente. Si ha recibido una PDU,
     * mal, tras enviar una confirmaci&@243n negativa, esperar&#225 la 
     * retransmisi&#243n de ese mismo paquete. Para cualquier 
     * otro caso, se devuelve una excepci&#243n de tipo <b>ExcepcionDidacCom</b>.
     * 
     * @return Devuelve la PDU recibida sin errores.
     * @throws ExcepcionDidacCom 
     */

    @Override
    public IDUDidacCom recibir() throws ExcepcionDidacCom
    {
        IDUDidacCom idu;         //IDU que se facilitará al nivel superior   
        int tries= 0;            //Número de intentos
        byte PDUD_Type;          //Tipo de PDU que se recibirá
        byte PDUC_Type;          //Se inicializa a ACK y cambia si hay errores
        byte lengthData= 0x00;   //Longitud del campo datos del usuario iniciado
        int PDULength;           //Longitud de la PDU recibida
        
         //Abrir un stream para copiar info en PDUControl más adelante           
         ByteArrayOutputStream baos = new ByteArrayOutputStream();
         DataOutputStream DataOut = new DataOutputStream(baos);
        
        try{
            /* Arrays de bytes destinados a albergar PDU´s de tipo datos y 
             * control*/
            byte[] PDUControl= new byte [MIN_LONG_PDU];
            byte[] PDUData= new byte [MAX_LONG_PDU];
            
            if (canal== null)
            {
                throw new ExcepcionDidacCom("No se ha abierto el canal");   
            }
            /*Array de bytes donde albergar la PDU tipo datos contenida en un 
            * datagrama a su llegada*/
            DatagramPacket datagrama= new DatagramPacket(PDUData, MAX_LONG_PDU);
            do{
                tries++;
                PDUC_Type= PDU_ACK;
                /*   Primer paso, recibir una PDU de datos. Su estructura es la 
                * siguiente:
                *           _____________________________________
                *           |Tipo PDU|Long.Datos |Datos |  MD5  |
                *           |  0x00  |  1 byte   |0-20B | 16 B  |
                *           -------------------------------------
                */ 
                canal.receive(datagrama);   //Se recibe el datagrama
                
                /*    Después se comprueba si su longitud es correcta, para 
                * después validar su código hash y más tarde determinar si los 
                * campos tipo y longitud datos son correctos. Si hay algún 
                * error, se enviará de vuelta una confirmación negativa. 
                *    Por tanto, la estructura de la PDU de control es la 
                * siguiente:
                *           _____________________________________
                *           |Tipo PDU|Long.Datos | Datos |  MD5  |
                *           |0xFF/FE |   0x00    |0 bytes| 16 B  |
                *           -------------------------------------
                *    Cuyo campo Tipo PDU variará en función de si se detecta 
                * algún error según se van comprobando.
                */
                PDULength= datagrama.getLength();
                if(PDULength<MIN_LONG_PDU)  PDUC_Type=PDU_NACK;
                else{
                /*Si el hash no es correcto, o si en comprobarHash salta una 
                 * excepción porque el campo longitud datos no se corresponde
                 * con el debido, se creará un NACK*/
                    try{
                        if (!comprobarHash(PDUData, PDULength)) 
                            PDUC_Type= PDU_NACK;
                    }catch (ExcepcionDidacCom e)   {PDUC_Type=PDU_NACK;}
                    
                    if (PDUC_Type== PDU_ACK) //Para saltarse código si no.
                    {
                        //Abrir un stream para leer info en PDUData
                        ByteArrayInputStream bais= 
                                           new ByteArrayInputStream(PDUData);
                        DataInputStream DataIn= new DataInputStream(bais);
                        PDUD_Type = DataIn.readByte(); //Copia el tipo de PDU
                        lengthData= DataIn.readByte(); //Copia la long. de datos
                        //Si no es de tipo datos
                        if ((PDUD_Type!= PDU_DATOS))   PDUC_Type= PDU_NACK;
                    }
                }
                //Serializar los datos para crear la PDU de control
                DataOut.write(PDUC_Type);    //Campo Tipo
                DataOut.write(PDU_DATOS);    //Campo "longitud de los datos" = 0
                PDUControl = baos.toByteArray();
                /*El Hash se calculará para todos los campos serializados de 
                * PDUControl, a la vez que se copia en el Stream DataOut.*/
                DataOut.write (GestorHash.generarHash(PDUControl));
                //Se serializa en PDUControl
                PDUControl = baos.toByteArray();
                //Se crea un datagrama que envíe la PDU de control
                DatagramPacket ControlDatagram = new DatagramPacket(PDUControl,
                                                                  MIN_LONG_PDU);
                canal.send(ControlDatagram);    //Se envía la confirmación
    
            }while ((tries<= N_REINTENTOS) && (PDUC_Type== PDU_NACK));
            
            /* Si todo ha ido bien, se devolverá una IDU al nivel superior, 
             * creada a continuación. En caso contrario se lanzará una 
             * excepción indicando que se ha superado el número de reintentos*/
            if (PDUC_Type== PDU_NACK)
            {
                throw new ExcepcionDidacCom ("Error al recibir la PDU, se ha "
                        + "superado el numero de reintentos");
            }else
            {
               //Extraer datos de la PDU de datos y el datagrama que lo contenía
               int port = datagrama.getPort();              //Puerto para la IDU
               String IP = datagrama.getAddress().toString();//IP para la IDU 
               //lengthData es la longitud del campo datos 
               //Byte de datos  en la IDU
               byte[] datos = Arrays.copyOfRange(PDUData, 1,lengthData);
               //Se forma la IDU con la información obtenida
               idu = new IDUDidacCom(IP, port, datos, lengthData);
            }
        
        }catch(IOException e) 
        {
            throw new ExcepcionDidacCom("Ha habido un error"+e.getCause());
        }    
        return idu;
}	           
        
    /**
    *
    */
    public DidacComImpl()
    {
        // Inicializar el canal
    }
    
    /*
     * 
     */
    @Override
    public void iniciar() throws ExcepcionDidacCom 
    {
        try   {canal = new DatagramSocket();} 
        catch (SocketException e)   {throw new ExcepcionDidacCom("Error al "
                + "iniciar:\n  " +e.getCause());}
    }

    @Override
    public void iniciar(int puertoLocal) throws ExcepcionDidacCom 
    {
        try   {canal = new DatagramSocket(puertoLocal);} 
	catch (SocketException e) {throw new ExcepcionDidacCom("Error al "
                + "iniciar canal definiendo un puerto:\n  " +e.getCause());}
    }

    @Override
    public void parar() throws ExcepcionDidacCom 
    {
        if (canal != null)   canal.close();
    }
	
	/**
	 * Comprueba si el hash de una PDU es correcto.
	 * 
	 * @param secuencia Secuencia de bytes que contiene la PDU a analizar. 
	 * @param longitud Longitud de la PDU que se pasa como par&#225metro en 
         * <b><i>secuencia</i></b>.
	 * @return Devuelve <i>true</i> si la PDU incluye un campo hash correcto
         * y <i>false</i> en caso contrario.
	 * @throws IOException Se lanza al detectar un error al extraer de la 
         *                     secuencia de bytes los diferentes campos.
	 * @throws ExcepcionDidacCom Se lanza si la estructura de campos de la 
         *                           PDU es incorrecta.
	 */
	private boolean comprobarHash( byte []secuencia, int longitud) 
                                        throws IOException, ExcepcionDidacCom
        {
            if (longitud > secuencia.length)
            {
                throw new IOException("La longitud no se corresponde con la "
                        + "esperada");
            }
            boolean result; //Devolverá true si todo es correcto
            //Dividir 
            byte[] secuenciaData= Arrays.copyOfRange(secuencia, 0, 
                                       longitud-GestorHash.LONG_HASH);
            byte[] secuenciaHash= Arrays.copyOfRange (secuencia, 
                                       longitud-GestorHash.LONG_HASH, longitud);
            result = GestorHash.validarHash(secuenciaData, secuenciaHash);
                        
            return result;
	}
}
