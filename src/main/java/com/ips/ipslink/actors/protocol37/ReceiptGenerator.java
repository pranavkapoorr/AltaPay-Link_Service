package com.ips.ipslink.actors.protocol37;

import java.util.*;
import java.util.concurrent.TimeUnit;
import org.apache.logging.log4j.*;
import com.fasterxml.jackson.annotation.JsonInclude.Include;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ips.ipslink.actormessages.*;
import com.ips.ipslink.marshallers.ResponseJson;
import com.ips.ipslink.protocol37.Protocol37Receipt;

import akka.actor.*;

public class ReceiptGenerator extends AbstractActor{
	private final static Logger log = LogManager.getLogger(ReceiptGenerator.class);
	private final StringBuffer receiptBuffer = new StringBuffer();
	private final StringBuilder receipt = new StringBuilder();
	private final ObjectMapper mapper;
	private final static char newLine = (char)10;
	private final boolean  printOnECR;
	private final ResponseJson receipt_Json;
	private final HashMap<String, ArrayList<String>> languageDictionary;
	private final Protocol37Receipt p37receipt;
	private final long amount;
	private final boolean wait4CardRemoval;
	private final boolean isTerminalStatus;
	private final boolean isAdvance;
	private boolean cardRemoved;
	private boolean gotData;
	
	public static Props props(boolean printOnECR,HashMap<String, ArrayList<String>> languageDictionary, long amount,boolean wait4CardRemoval, boolean isLastTransStatus,boolean isTerminalStatus, boolean isAdvance){
		return Props.create(ReceiptGenerator.class , printOnECR, languageDictionary, amount, wait4CardRemoval, isLastTransStatus, isTerminalStatus, isAdvance);
	}
	private ReceiptGenerator(boolean printOnECR,HashMap<String, ArrayList<String>> languageDictionary, long amount, boolean wait4CardRemoval,boolean isLastTransStatus,boolean isTerminalStatus, boolean isAdvance) {
	    this.languageDictionary = languageDictionary;
		this.printOnECR = printOnECR;
		this.amount = amount;
		this.isAdvance = isAdvance;
		this.wait4CardRemoval = wait4CardRemoval;
		this.mapper = new ObjectMapper();
		this.mapper.setSerializationInclusion(Include.NON_NULL);
		this.receipt_Json = new ResponseJson();
		this.isTerminalStatus = isTerminalStatus;
	    this.p37receipt = new Protocol37Receipt(isLastTransStatus);
	    this.gotData = false;
	}
	
	@Override
	public void preStart() throws Exception {
	    log.info(getSelf().path().name()+" starting Receipt Generator");
	}
	
	@Override
	public Receive createReceive() {
		return receiveBuilder().match(String.class, message->{
			
		/***checks if the current String received is a receipt message and then puts it in to String buffer for formatting it***/
				if(message.contains("0S")){
					/***removes terminal id from each message**/
					receiptBuffer.append(message.substring(10, message.length()));
					/**checks if this is the last cycle of receipt depending on the delimiter**/
					if(message.charAt(message.length()-1) == (char)27){//last cycle
						String tempReceipt =  receiptBuffer.toString().replace(String.valueOf((char)127), "").replaceAll("}", "").replace(String.valueOf((char)27), "");
						receipt.append(tempReceipt);
						/**formatting receipt adding "/n" **/
						for(int i = 24; i< receipt.length(); i+=25){
							receipt.insert(i,newLine);//"\n");
						}
						receipt_Json.setReceipt(receipt.toString());
						/**sends out the receipt if printOnECR is enabled ie no S message will be expected but U message will be if GT bit is on**/
						if(printOnECR ){
						    log.info(getSelf().path().name()+" Receipt Generated");
						    getContext().getParent().tell(new ReceiptGenerated(true), getSelf());//telling parent that receipt generated
						    getSelf().tell(receipt_Json, getSelf());
						}
					}
					
					
				}
				/**checks if the received message is result of reversal, payment etc**/
				else if(message.contains("0E0")){
					appDataReceived();
					String terminalId = message.substring(0, 8);
					String cardType = message.substring(47, 48);
					String aquirerId = message.substring(48,59);
					String STAN = message.substring(59,65);
					String progressiveNum = message.substring(65,71);
					String actionCode = message.substring(71,74);
					receipt_Json.setTerminalId(terminalId);
                    receipt_Json.setAquirerId(aquirerId);
                    receipt_Json.setSTAN(STAN);
                    receipt_Json.setActionCode(actionCode);
                    receipt_Json.setProgressiveNumber(progressiveNum);
                    receipt_Json.setAmount(String.valueOf(amount));
					if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("00")){
						
						String cardPan = message.substring(12,31);
						String transacType = message.substring(31,34);
						String authCode = message.substring(34,40);
						String transTime = message.substring(40,47);
						receipt_Json.setTransactionStatus("OK");
						receipt_Json.setTransactionStatusText("Transaction Successful");
						receipt_Json.setCardPAN(cardPan);
						receipt_Json.setTransactionType(transacType);
						receipt_Json.setAuthCode(authCode);
						receipt_Json.setTransactionDate(p37receipt.getCurrentDate());
		                receipt_Json.setTransactionTime(p37receipt.getCurrentTime());
				}
					else if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("01")){
						String reason4Failure = message.substring(12,36);
						receipt_Json.setTransactionStatus("KO");
						receipt_Json.setTransactionStatusText(reason4Failure);
					}else if(message.substring(message.indexOf('E')+1, message.indexOf('E')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTransactionStatus("KO");
					    receipt_Json.setTransactionStatusText("***unexpected***");
					}	
				}
				/**checks if the received message is result of DCC transaction**/
				else if(message.contains("0V0")){
					appDataReceived();
					String terminalId = message.substring(0, 8);
					String cardType = message.substring(47, 48);
					String aquirerId = message.substring(48,59);
					String STAN = message.substring(59,65);
					String progressiveNum = message.substring(65,71);
					String actionCode = message.substring(71,74);
					String amountV = message.substring(74,82);
					String currencyV = message.substring(82,83);
					String conversionRate = message.substring(83,91);
					String currencyCode = message.substring(91,94);
					String transactionAmount = message.substring(94,106);
					String transactionCurrencyDecimal = message.substring(106,107);
					receipt_Json.setTerminalId(terminalId);
					receipt_Json.setAmount(String.valueOf(amount));
                    receipt_Json.setAquirerId(aquirerId);
                    receipt_Json.setSTAN(STAN);
                    receipt_Json.setActionCode(actionCode);
                    receipt_Json.setProgressiveNumber(progressiveNum);
                    receipt_Json.setDccAmount(amountV);
                    receipt_Json.setDccCurrency(currencyV);
                    receipt_Json.setDccCurrencyCode(currencyCode);
                    receipt_Json.setDccConversionRate(conversionRate);
                    receipt_Json.setDccTransactionAmount(transactionAmount);
                    receipt_Json.setDccTransactionCurrencyDecimal(transactionCurrencyDecimal);
					if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("00")){	
						String cardPan = message.substring(12,31);
						String transacType = message.substring(31,34);
						String authCode = message.substring(34,40);
						String transTime = message.substring(40,47);
						receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
						receipt_Json.setCardPAN(cardPan);
                        receipt_Json.setTransactionType(transacType);
                        receipt_Json.setAuthCode(authCode);
                        receipt_Json.setTransactionDate(p37receipt.getCurrentDate());
                        receipt_Json.setTransactionTime(p37receipt.getCurrentTime());
					}else if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("01")){
						String reason4Failure = message.substring(12,36);
						receipt_Json.setTransactionStatus("KO");
						receipt_Json.setTransactionStatusText(reason4Failure);
					}else if(message.substring(message.indexOf('V')+1, message.indexOf('V')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTransactionStatus("KO");
					    receipt_Json.setTransactionStatusText("**UNEXPECTED TAG**");
					}	
				}
				/**checks if the received message is result of REFUND transaction**/
				else if(message.contains("0A0")){
					appDataReceived();
					String terminalId = message.substring(0, 8);
					String cardPan = message.substring(12,31);
					String transacType = message.substring(31,34);
					String authCode = message.substring(34,40);
					String aquirerId = message.substring(40,51);
					String transTime = null;
					if(message.length()>=58) {
						transTime = message.substring(51,58);
					}else {
						transTime = "unexpected";
					}
					receipt_Json.setTerminalId(terminalId);
					receipt_Json.setAmount(String.valueOf(amount));
					receipt_Json.setCardPAN(cardPan);
					receipt_Json.setTransactionType(transacType);
					receipt_Json.setAuthCode(authCode);
					receipt_Json.setAquirerId(aquirerId);
					if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("00")){ }else if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("01")){
					    receipt_Json.setTransactionStatus("KO");
					}else if(message.substring(message.indexOf('A')+1, message.indexOf('A')+3).equalsIgnoreCase("09")){
					    receipt_Json.setTransactionStatus("KO");
					    receipt_Json.setTransactionStatusText("**UNEXPECTED TAG**");
					}	
				}
				/**checks if the received message is result of TERMINAL STATUS transaction**/
				else if(message.contains("0T0")){
					appDataReceived();
					String terminalId = message.substring(0, 8);
					String totalInEur = message.substring(12,28);
					String actionCode = message.substring(28,31);
					if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("00")){
						if(isTerminalStatus){
						    receipt_Json.setPedConnectivity("OK");
						    receipt_Json.setGatewayConnectivity("OK");
						}else{
							receipt_Json.setTerminalId(terminalId);
							receipt_Json.setTransactionStatus("OK");
	                        receipt_Json.setTransactionStatusText("Transaction Successful");
							receipt_Json.setAmount(totalInEur);
							receipt_Json.setActionCode(actionCode);
						}
					}else if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("01")){
					    if(isTerminalStatus){
                            receipt_Json.setPedConnectivity("OK");
                            receipt_Json.setGatewayConnectivity("KO");
                        }else{
                            receipt_Json.setTerminalId(terminalId);
    					    receipt_Json.setTransactionStatus("KO");
                            receipt_Json.setTransactionStatusText("Transaction Unsuccessful");
                            receipt_Json.setAmount(totalInEur);
                            receipt_Json.setActionCode(actionCode);
                        }
					}else if(message.substring(message.indexOf('T')+1, message.indexOf('T')+3).equalsIgnoreCase("09")){
					    if(isTerminalStatus){
                            receipt_Json.setPedConnectivity("OK");
                            receipt_Json.setGatewayConnectivity("KO");
                        }else{
    					    receipt_Json.setTerminalId(terminalId);
                            receipt_Json.setTransactionStatus("KO");
                            receipt_Json.setTransactionStatusText("**UNEXPECTED TAG**");
                            receipt_Json.setAmount(totalInEur);
                            receipt_Json.setActionCode(actionCode);
                        }
					}	
				}
				else if(message.contains("0C0")){
					appDataReceived();
					String terminalId = message.substring(0, 8);
					if(message.substring(message.indexOf('C')+1, message.indexOf('C')+3).equalsIgnoreCase("00")){
						String totalInEur = message.substring(12,28);
						String totalInEurRecByHost = message.substring(28,44);
						String actionCode = null;
								if(message.length()>=47){
									actionCode = message.substring(44,47);
								}else {
									actionCode = "unexpected";
								}
						receipt_Json.setTerminalId(terminalId);
						receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
                        receipt_Json.setAmount(totalInEur);
                        //receipt_Json.setHostTotalAmountReqByHost(totalInEurRecByHost);
                        receipt_Json.setActionCode(actionCode);
					}else if(message.substring(message.indexOf('C')+1, message.indexOf('C')+3).equalsIgnoreCase("01")){
						String failureReason = message.substring(12,31);
						String actionCode = message.substring(31,34);
						 receipt_Json.setTerminalId(terminalId);
						 receipt_Json.setTransactionStatus("KO");
	                     receipt_Json.setActionCode(actionCode);
	                     receipt_Json.setTransactionStatusText(failureReason);
					}
				}
				/**checks if the received message is result of DLL transaction**/
				else if(message.contains("0D0")){
					appDataReceived();
					String terminalId = message.substring(0, 8);
					String STAN = message.substring(12,18);
					String progrNum = message.substring(18,24);
					receipt_Json.setTerminalId(terminalId);
					receipt_Json.setSTAN(STAN);
					receipt_Json.setProgressiveNumber(progrNum);
					if(message.substring(message.indexOf('D')+1, message.indexOf('D')+3).equalsIgnoreCase("00")){
						//String timeData = message.substring(24,31);
					    receipt_Json.setTransactionStatus("OK");
                        receipt_Json.setTransactionStatusText("Transaction Successful");
						//receipt_Json.setTransactionStatusText(timeData);
					}else if(message.substring(message.indexOf('D')+1, message.indexOf('D')+3).equalsIgnoreCase("01")){
						String failureReason = message.substring(24,48);
						receipt_Json.setTransactionStatus("KO");
						receipt_Json.setTransactionStatusText(failureReason);
					}
				}
				/**checks if the received message is result of ProbePed transaction**/
                else if(message.contains("0s0")){
                	appDataReceived();
                    String terminalId = message.substring(0,8);
                    String date = message.substring(20,30);
                    String status = message.substring(30,31);
                    String data = message.substring(31);
                    receipt_Json.setTerminalId(terminalId);
                    receipt_Json.setTransactionStatus("OK");
                    receipt_Json.setTransactionStatusText("Transaction Successful");
                    receipt_Json.setPedDate(date.substring(0,6));
                    receipt_Json.setPedTime(date.substring(6));
                    receipt_Json.setPedStatus(status);
                    receipt_Json.setFirmwareVersion(data.substring(data.indexOf("EMV")+3,data.indexOf("EMV")+3+4));
                    receipt_Json.setPartNumber(data.substring(data.indexOf("P/N")+3,data.indexOf("P/N")+3+6));
                    receipt_Json.setSerialNumber(data.substring(data.indexOf("S/N")+3));
                }
				/**checks if the received message is result of ADDITIONAL DATA FROM GT transaction**/
				else if(message.contains("0U0")){
					appDataReceived();
					int length = Integer.parseInt(message.substring(16,19));
					if(length>0){
					    String AdditionalGtData = message.substring(19,19+length);
					    receipt_Json.setCardPresentToken(AdditionalGtData.substring(0, 19));
					    if(length>21){
					        receipt_Json.setOmniChannelToken(AdditionalGtData.substring(20, 110));
					        receipt_Json.setOmniChannelGUID(AdditionalGtData.substring(111));
					}
					    }
	
					if(!printOnECR  && isAdvance){
					    getSelf().tell(receipt_Json, getSelf());
					}
				}
				
					if((!printOnECR || isTerminalStatus) && !isAdvance){
					    getSelf().tell(receipt_Json, getSelf());
					}
				
		}).match(ResponseJson.class, receiptX->{
		            if(wait4CardRemoval){
		                /**it is implemented in @StatusMessageSender.java**/
		                if(this.cardRemoved){
		                    p37receipt.generateJsonReceipt(log,getSelf(),getContext(),mapper,languageDictionary,receiptX);
		                }else{
		                    TimeUnit.NANOSECONDS.sleep(1);
		                    getSelf().tell(receiptX, getSelf());
		                }
		            }else{
		                p37receipt.generateJsonReceipt(log,getSelf(),getContext(),mapper,languageDictionary,receiptX);
		            }
		})
		.match(FailedAttempt.class, fa ->{//if faled attempt received due to overwaiting for response then just send the half details.
			log.info(getSelf().path().name()+" failed attemp received..........");
			if(gotData) {
				getSelf().tell(receipt_Json, getSelf());
			} else{
				getContext().getParent().tell(fa, getSelf());
			}
		})
		.match(CardRemoved.class,cR -> this.cardRemoved = cR.cardRemoved())
		 .build();
	}
	/**truns on got data if application msg is received so send receipt as result**/
	private void appDataReceived() {
		log.info(getSelf().path().name()+" gotAppData.");
		this.gotData = true;
	}
	
	
	@Override
	public void postStop() throws Exception {
		log.info(getSelf().path().name()+" stopping Receipt Generator");
	}
}
