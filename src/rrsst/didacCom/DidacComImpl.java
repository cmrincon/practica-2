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

    @Override
    public void enviar(IDUDidacCom idu) 
            throws ExcepcionDidacCom, IllegalArgumentException
    {
        int tries= 0;   //Numero de intentos
        byte PDUType;   //Tipo de PDU que se recibirá
        byte lengthData;//Longitud del campo datos del usuario
        try{
            byte[] PDUControl= new byte [MIN_LONG_PDU];
            byte[] PDUData;
            
            if (canal== null)
            {
                throw new ExcepcionDidacCom("No se ha abierto el canal");   
            }
            //Abrir un stream para copiar info en PDUData más adelante           
            ByteArrayOutputStream baos = new ByteArrayOutputStream();
            DataOutputStream DataOut = new DataOutputStream(baos);
            
            //Abrir un stream para leer info en PDUControl más adelante  
            ByteArrayInputStream bais = null;
            DataInputStream DataIn = new DataInputStream(bais);
            
            tries++;     //Número de reintentos
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
            DataOut.write(datos);           //Campo de datos
            //Se copia a PDUData
            PDUData = baos.toByteArray();
            /*El Hash se calculará para todos los campos serializados de la 
            PDU, a la ver que se copia en el Stream DataOut.*/
            DataOut.write (GestorHash.generarHash(PDUData));
            //Se copia a PDUData
            PDUData = baos.toByteArray();
            
            //Se crea un datagrama que enviará la PDU de datos PDUData
            DatagramPacket datagrama = new DatagramPacket(PDUData, 
                    PDUData.length, InetAddress.getByName(IP), port);
            
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
            DatagramPacket ControlDatagram = new DatagramPacket(PDUControl,
                                                                  MIN_LONG_PDU);
            
            //Se enviará y reenviará si la recepción no es correcta
            do{      
                canal.send(datagrama);         //Envío del datagrama con PDUData
                canal.receive(ControlDatagram); //Recibo del datagrama PDUControl

                //Si el hash de no es correcto    
		if(PDUControl.length != 18)
			throw new ExcepcionDidacCom("Intento "+tries+".Longitud de PDU de" +
				"control erronea ["+PDUControl.length+"]");
		else{
			
		
			if (!comprobarHash(PDUControl, MIN_LONG_PDU)) 
              	 	     throw new ExcepcionDidacCom ("Intento "+tries+" Hash de la "
              	 	             + "PDU de control incorrecto");
             	  	 else //Si lo es
               		 {
                  		  bais = new ByteArrayInputStream(PDUControl);
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
                
            }while ((tries<= N_REINTENTOS) && (PDUType == PDU_NACK));
        
        }catch(IOException e) 
        {
            throw new ExcepcionDidacCom("Ha habido un error");
        }
        
        
    }
        
        @Override
    public IDUDidacCom recibir() throws ExcepcionDidacCom
        {
            IDUDidacCom idu;
            byte[] datos = new byte[20];
            DatagramPacket datagrama = new DatagramPacket(datos,20);
            try
            {
                canal.receive(datagrama);
                idu = new IDUDidacCom (datagrama.getAddress().getHostAddress(), 
                        datagrama.getPort(), datagrama.getData(), 
                        datagrama.getLength());
            }catch (IOException e)   {throw new ExcepcionDidacCom("Error en la "
                    + "recepción: " + e.getMessage());}
            return idu;
		
		// Crear Datagrama UDP a enviar o recibir.

		
		// Crear el stream contenedor de la PDU a enviar.

		
		// Crear el stream contenedor de la PDU de confirmaci�n a recibir.

		
		// Validar que el canal se ha creado.

		
		// Inicializar el contador de reenv�os.

		
		// Validar que el parametro idu es correcto.


		// Construir la PDU de datos del nivel DidacCom
		// lanzando las excepciones oportunas

		
		// Entrar en el bluce de envio y recepci�n
		// Salir cuando se reciba un ACK (transmisi�n correcta)
		// o cuando se haya sobrepasado el n�mero m�ximo de reenvios
		// por haber recibido sucesivos NACKs (transmisi�n incorrecta)
		
		// *******************************************************************************************
		
			// Contruir el datagrama que contenga la PDU de datos del nivel DidacCom
			// lanzando las excepciones oportunas

		
			// Enviar el datagrama a trav�s del canal
			// lanzando las excepciones oportunas
			
			
			// Contruir el datagrama que contendr� la PDU ACK o NACK procedente del servidor
			// lanzando las excepciones oportunas		
			
			
			// Recibir el datagrama de respuesta trav�s del canal
			// lanzando las excepciones oportunas
			

		
			// Procesar la PDU recibida:
			
			// Comprobar que su longitud est� dentro del rango permitido
			// lanzando las excepciones oportunas

			
			// Crear el stream de entrada que contendr� los bytes de la PDU recibida

			
			// Comprobar que el hash de la PDU recibida es correcto
			// lanzando las excepciones oportunas
			
			// Comprobar que tipo de la PDU recibida es correcto (debe ser ACK o NACK)
			// lanzando las excepciones oportunas
			
			// Comprobar que la longitud del campo datos de la PDU recibida es coherente con el tipo de pdu
			// lanzando las excepciones oportunas
			
		// *******************************************************************************************
		
		
		// Si finalmente se ha salido del bucle por "Transmisi�n incorrecta"
		// lanzar las excepciones oportunas

    

 
        
		// Validar que el canal se ha creado.
			
		// Inicializar el contador de reenv�os ACKs enviados.
		

		// Entrar en el bluce de recepci�n y env�o
		// Salir cuando se haya sobrepasado el n�mero m�ximo de NACKs enviados
		// o cuando el hash de la PDU recibida sea incorrecto (errores de transmisi�n)
		
		// *******************************************************************************************		
		
			// Recibir el datagrama
			// lanzando las excepciones oportunas
	
			
			// Procesar la PDU recibida:
			
			// Comprobar que su longitud est� dentro del rango permitido
			// lanzando las excepciones oportunas
			

			// Comprobar que el hash de la PDU recibida es correcto
			// lanzando las excepciones oportunas		
			
			
			//Si el hash es correcto
			
				// Se comprueba que la PDU recibida sea de datos y con una longitud coherente
				// lanzando las excepciones oportunas
				// Si todas las comprobaciones son correctas 
				// el tipo de la PDU de respuesta sera ACK.
			
			// Si el hash no es correcto
			
				// el tipo de la PDU de respuesta sera NACK.
			

			// Generar la PDU de respuesta a enviar, ACK o NACK dependiendo de si el
			// hash de la PDU recibida ha sido correcto o no.
			

			// Enviar la PDU de confirmacion a trav�s del canal.

		// *******************************************************************************************		
		
		// Si finalmente se ha salido del bucle por alg�n hash incorrecto
		// lanzar las excepciones oportunas		
		
		
		// Si finalmente se ha salido del bucle por exceder el n�mero de NACKs enviados
		// lanzar las excepciones oportunas		
		
		
		// Si se llega hasta aqu� es que la recepci�n de la PDU de datos ha sido correcta
		// y se ha enviado ACK
		
		// Componer la IDU para nivel superior		
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
