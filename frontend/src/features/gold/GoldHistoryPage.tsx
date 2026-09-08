import { useState } from 'react';
import { useQuery } from '@tanstack/react-query';
import { goldService } from '../../services/goldService';
import { CACHE_TIME } from '../../config/queryConfig';
import TransactionItem from './components/TransactionItem';
import './GoldPage.css';

type FilterType = 'ALL' | 'CHARGE' | 'SPEND' | 'REWARD' | 'REFUND';

function GoldHistoryPage() {
  const [filter, setFilter] = useState<FilterType>('ALL');
  const [page, setPage] = useState(0);

  const { data: balanceData } = useQuery({
    queryKey: ['gold', 'balance'],
    queryFn: goldService.getBalance,
    ...CACHE_TIME.DYNAMIC,
  });

  const { data: transactionsData } = useQuery({
    queryKey: ['gold', 'transactions', filter, page],
    queryFn: () => goldService.getTransactions(page, 20, filter === 'ALL' ? undefined : filter),
    ...CACHE_TIME.DYNAMIC,
  });

  const balance = balanceData?.balance ?? 0;
  const transactions = transactionsData?.content ?? [];
  const totalPages = transactionsData?.totalPages ?? 0;
  const hasMore = page < totalPages - 1;

  const handleLoadMore = () => {
    if (hasMore) {
      setPage((prev) => prev + 1);
    }
  };

  const handleFilterChange = (newFilter: FilterType) => {
    setFilter(newFilter);
    setPage(0);
  };

  return (
    <div className="gold-container">
      <div className="gold-balance-small">
        <span className="label">보유 골드</span>
        <span className="amount">{balance.toLocaleString()}G</span>
      </div>

      <div className="gold-filter-tabs">
        <button
          className={filter === 'ALL' ? 'active' : ''}
          onClick={() => handleFilterChange('ALL')}
        >
          전체
        </button>
        <button
          className={filter === 'CHARGE' ? 'active' : ''}
          onClick={() => handleFilterChange('CHARGE')}
        >
          충전
        </button>
        <button
          className={filter === 'SPEND' ? 'active' : ''}
          onClick={() => handleFilterChange('SPEND')}
        >
          사용
        </button>
        <button
          className={filter === 'REWARD' ? 'active' : ''}
          onClick={() => handleFilterChange('REWARD')}
        >
          적립
        </button>
        <button
          className={filter === 'REFUND' ? 'active' : ''}
          onClick={() => handleFilterChange('REFUND')}
        >
          환불
        </button>
      </div>

      <div>
        {transactions.map((transaction) => (
          <TransactionItem key={transaction.id} transaction={transaction} />
        ))}
      </div>

      {hasMore && (
        <div className="gold-load-more">
          <button onClick={handleLoadMore} className="gold-load-more-btn">
            더 보기
          </button>
        </div>
      )}
    </div>
  );
}

export default GoldHistoryPage;
