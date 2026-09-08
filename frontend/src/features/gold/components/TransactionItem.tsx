import { type GoldTransaction } from '../../../services/goldService';
import '../GoldPage.css';

interface TransactionItemProps {
  transaction: GoldTransaction;
}

function TransactionItem({ transaction }: TransactionItemProps) {
  const isPositive = transaction.amount > 0;
  const amountClass = isPositive ? 'earn' : 'spend';
  const amountPrefix = isPositive ? '+' : '-';

  return (
    <div className="transaction-item">
      <div className="tx-info">
        <div className="tx-desc">{transaction.description}</div>
        <div className="tx-date">
          {transaction.createdAt
            ? new Date(transaction.createdAt).toLocaleDateString('ko-KR', {
                year: 'numeric',
                month: '2-digit',
                day: '2-digit',
                hour: '2-digit',
                minute: '2-digit',
              })
            : ''}
        </div>
      </div>
      <div className={`tx-amount ${amountClass}`}>
        {amountPrefix}{Math.abs(transaction.amount).toLocaleString()}G
      </div>
    </div>
  );
}

export default TransactionItem;
