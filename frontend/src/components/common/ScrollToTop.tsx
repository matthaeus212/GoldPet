import { useEffect } from 'react';
import { useLocation } from 'react-router-dom';

export default function ScrollToTop() {
  const { pathname } = useLocation();

  useEffect(() => {
    window.scrollTo(0, 0);
    const root = document.getElementById('root');
    if (root) {
      root.scrollTop = 0;
    }
    document.body.scrollTop = 0;
  }, [pathname]);

  return null;
}
