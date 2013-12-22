package net.sf.tail;


/**
 * Strategy é toda a classe que pode receber como parâmetro um {@link Indicator}
 * ou uma outra Strategy, e retornar uma {@link Operation} dado um index.
 * 
 */
public interface Strategy {

	/**
	 * Retorna um boolean true, caso avalie necessario, recomendando uma
	 * determinada acao. Caso contrario retorna false, nao recomendando nenhuma
	 * acao.
	 * 
	 * @param trade
	 * @param index
	 * @return
	 */
	boolean shouldOperate(Trade trade, int index);

	boolean shouldEnter(int index);

	boolean shouldExit(int index);
	
	Strategy and(Strategy strategy);
	
	Strategy or(Strategy strategy);

	String getName();
}
