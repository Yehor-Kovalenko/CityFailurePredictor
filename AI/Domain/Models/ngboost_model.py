import joblib
import numpy as np

from ngboost import NGBRegressor
from ngboost.distns import Normal
from ngboost.learners import default_tree_learner


class NGBoostModel:

    def __init__(
        self,
        n_estimators=500,
        learning_rate=0.01,
        minibatch_frac=1.0,
        max_depth=3,
        verbose=False,
        random_state=42,
    ):

        self.model = NGBRegressor(
            Dist=Normal,
            Base=default_tree_learner,
            n_estimators=n_estimators,
            learning_rate=learning_rate,
            minibatch_frac=minibatch_frac,
            random_state=random_state,
            verbose=verbose,
        )

    def train(self, X: np.ndarray, y: np.ndarray):
        self.model.fit(X, y)

    def predict(self, X: np.ndarray):
        """
        Point prediction (mean).
        """
        return self.model.predict(X)

    def predict_distribution(self, X: np.ndarray):
        """
        Full NGBoost distribution object.
        """
        return self.model.pred_dist(X)

    def predict_mean_std(self, X: np.ndarray):
        """
        Returns:
            mean, std
        """
        dist = self.model.pred_dist(X)

        return (
            np.asarray(dist.loc),
            np.asarray(dist.scale)
        )

    def predict_interval(
        self,
        X: np.ndarray,
        confidence: float = 0.95
    ):
        """
        Returns prediction interval.

        Example:
            lower, upper = model.predict_interval(X)
        """

        dist = self.model.pred_dist(X)

        alpha = (1.0 - confidence) / 2.0

        lower = dist.ppf(alpha)
        upper = dist.ppf(1.0 - alpha)

        return (
            np.asarray(lower),
            np.asarray(upper)
        )

    def save(self, path: str):
        joblib.dump(self.model, path)

    def load(self, path: str):
        self.model = joblib.load(path)